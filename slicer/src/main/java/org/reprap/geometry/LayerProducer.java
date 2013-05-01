package org.reprap.geometry;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Preferences;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.Attributes;

class LayerProducer {
    private static final Logger LOGGER = LogManager.getLogger(LayerProducer.class);
    private final SimulationPlotter simulationPlot;
    private final LayerRules layerRules;
    private final PolygonList allPolygons[];
    private final Preferences preferences = Preferences.getInstance();

    /**
     * Set up a normal layer
     */
    LayerProducer(final PolygonList ap[], final LayerRules lc, final SimulationPlotter simPlot) throws IOException {
        layerRules = lc;
        simulationPlot = simPlot;

        allPolygons = ap;

        if (simulationPlot != null) {
            if (!simulationPlot.isInitialised()) {
                final Rectangle rec = new Rectangle(lc.getBox());
                if (preferences.loadBool("Shield")) {
                    rec.expand(Point2D.add(rec.sw(), new Point2D(-7, -7))); // TODO: Yuk - this should be a parameter
                }
                simulationPlot.init(rec, "" + lc.getModelLayer() + " (z=" + lc.getModelZ() + ")");
            } else {
                try {
                    while (simulationPlot.isPauseSlicer()) {
                        Thread.sleep(1000);
                    }
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                }
                simulationPlot.cleanPolygons("" + lc.getModelLayer() + " (z=" + lc.getModelZ() + ")");
            }
        }
    }

    private Point2D posNow() {
        return new Point2D(layerRules.getPrinter().getX(), layerRules.getPrinter().getY());
    }

    /**
     * speed up for short lines
     */
    private boolean shortLine(final Point2D p, final boolean stopExtruder) {
        final GCodePrinter printer = layerRules.getPrinter();
        final double shortLen = printer.getExtruder().getShortLength();
        if (shortLen < 0) {
            return false;
        }
        final Point2D a = Point2D.sub(posNow(), p);
        final double amod = a.mod();
        if (amod > shortLen) {
            return false;
        }

        // TODO: FIX THIS
        //		printer.setSpeed(LinePrinter.speedFix(printer.getExtruder().getXYSpeed(), 
        //				printer.getExtruder().getShortSpeed()));
        printer.printTo(p.x(), p.y(), layerRules.getMachineZ(), printer.getExtruder().getShortLineFeedrate(), stopExtruder);
        return true;
    }

    /**
     * @param first
     *            First point, the end of the line segment to be plotted to from
     *            the current position.
     * @param second
     *            Second point, the end of the next line segment; used for angle
     *            calculations
     * @param feedrate
     *            The feed rate for this plot
     * @param turnOff
     *            True if the extruder should be turned off at the end of this
     *            segment.
     */
    private void plot(final Point2D first, final Point2D second, final double feedrate, final boolean stopExtruder) {
        if (shortLine(first, stopExtruder)) {
            return;
        }

        final GCodePrinter printer = layerRules.getPrinter();
        final double z = layerRules.getMachineZ();

        final double speedUpLength = printer.getExtruder().getAngleSpeedUpLength();
        if (speedUpLength > 0) {
            final SegmentSpeeds ss = SegmentSpeeds.createSegmentSpeeds(posNow(), first, speedUpLength);
            if (ss == null) {
                return;
            }

            printer.printTo(ss.getP1().x(), ss.getP1().y(), z, feedrate, false);

            if (ss.isPlotMiddle()) {
                //TODO: FIX THIS.
                //				int straightSpeed = LinePrinter.speedFix(currentSpeed, (1 - 
                //						printer.getExtruder().getAngleSpeedFactor()));
                //printer.setFeedrate(printer.getExtruder().getAngleFeedrate());
                printer.printTo(ss.getP2().x(), ss.getP2().y(), z, printer.getExtruder().getAngleFeedrate(), false);
            }

            printer.printTo(ss.getP3().x(), ss.getP3().y(), z, printer.getExtruder().getAngleFeedrate(), stopExtruder);
            // Leave speed set for the start of the next line.
        } else {
            printer.printTo(first.x(), first.y(), z, feedrate, stopExtruder);
        }
    }

    private void singleMove(final Point2D p) {
        final GCodePrinter pt = layerRules.getPrinter();
        pt.singleMove(p.x(), p.y(), pt.getZ(), pt.getFastXYFeedrate(), true);
    }

    private void move(final Point2D first, final boolean startUp, final boolean endUp) {
        final GCodePrinter printer = layerRules.getPrinter();
        final double z = layerRules.getMachineZ();
        printer.moveTo(first.x(), first.y(), z, printer.getExtruder().getFastXYFeedrate(), startUp, endUp);
    }

    /**
     * Plot a polygon
     * 
     * @throws IOException
     */
    private void plot(final Polygon polygon, final boolean firstOneInLayer) throws IOException {
        if (polygon.size() <= 1) {
            return;
        }

        // If the length of the plot is <0.05mm, don't bother with it.
        // This will not spot an attempt to plot 10,000 points in 1mm.
        double plotDist = 0;
        Point2D lastPoint = polygon.point(0);
        for (int i = 1; i < polygon.size(); i++) {
            final Point2D n = polygon.point(i);
            plotDist += Point2D.d(lastPoint, n);
            lastPoint = n;
        }

        if (plotDist < preferences.getMachineResolution() * 0.5) {
            LOGGER.debug("Rejected line with " + polygon.size() + " points, length: " + plotDist);
            return;
        }

        final GCodePrinter printer = layerRules.getPrinter();
        final double currentZ = printer.getZ();

        if (firstOneInLayer) {
            printer.singleMove(polygon.point(0).x(), polygon.point(0).y(), currentZ, printer.getFastXYFeedrate(), false);
            printer.forceNextExtruder();
        }
        final Attributes attributes = polygon.getAttributes();
        printer.selectExtruder(attributes);

        if (simulationPlot != null) {
            final PolygonList pgl = new PolygonList();
            pgl.add(polygon);
            simulationPlot.add(pgl);
        }

        final GCodeExtruder extruder = printer.getExtruder(attributes.getMaterial());
        final double outlineFeedrate = extruder.getOutlineFeedrate();
        final double infillFeedrate = extruder.getInfillFeedrate();
        final ExtrusionPath extrusionPath = new ExtrusionPath(polygon, polygon.isClosed() ? outlineFeedrate : infillFeedrate);
        plotExtrusionPath(extrusionPath, firstOneInLayer, extruder);
    }

    private void plotExtrusionPath(final ExtrusionPath extrusionPath, final boolean firstOneInLayer,
            final GCodeExtruder extruder) throws IOException {
        final double extrudeBackLength = extruder.getExtrusionOverRun();
        extrusionPath.backStepExtrude(extrudeBackLength);

        final GCodePrinter printer = layerRules.getPrinter();
        final double currentZ = printer.getZ();
        final double liftZ = extruder.getLift();
        if (liftZ > 0) {
            printer.singleMove(printer.getX(), printer.getY(), currentZ + liftZ, printer.getFastFeedrateZ(), true);
        }
        singleMove(extrusionPath.point(0));
        if (liftZ > 0) {
            printer.singleMove(printer.getX(), printer.getY(), currentZ, printer.getFastFeedrateZ(), true);
        }

        // Print any lead-in.
        printer.printStartDelay(firstOneInLayer);
        boolean extrudeOff = false;
        int pathLength = extrusionPath.size();
        if (extrusionPath.isClosed()) {
            // plot to each point(0..n) and then to point(0).
            pathLength++;
        }
        for (int i = 0; i < pathLength; i++) {
            final Point2D point = extrusionPath.point(i % extrusionPath.size());
            final Point2D next = extrusionPath.point((i + 1) % extrusionPath.size());
            final double feedrate = extrusionPath.speed(i % extrusionPath.size());
            final boolean oldexoff = extrudeOff;
            extrudeOff = (i > extrusionPath.extrudeEnd() && extrudeBackLength > 0) || i == pathLength - 1;
            plot(point, next, feedrate, extrudeOff);
            if (oldexoff ^ extrudeOff) {
                printer.retract();
            }
        }
        // If getMinLiftedZ() is negative, never lift the head
        final boolean lift = extruder.getMinLiftedZ() >= 0 || liftZ > 0;
        move(posNow(), lift, lift);
    }

    /**
     * Master plot function - draw everything. Supress border and/or hatch by
     * setting borderPolygons and/or hatchedPolygons null
     */
    void plot() throws IOException {
        boolean firstOneInLayer = true;

        for (final PolygonList pl : allPolygons) {
            for (int j = 0; j < pl.size(); j++) {
                plot(pl.polygon(j), firstOneInLayer);
                firstOneInLayer = false;
            }
        }
    }

}
