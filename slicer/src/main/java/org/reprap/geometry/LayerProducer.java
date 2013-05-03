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

    LayerProducer(final PolygonList ap[], final LayerRules lc, final SimulationPlotter simPlot) throws IOException {
        layerRules = lc;
        simulationPlot = simPlot;

        allPolygons = ap;

        if (simulationPlot != null) {
            if (!simulationPlot.isInitialised()) {
                final Rectangle rec = new Rectangle(lc.getBox());
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
            LOGGER.info("Rejected line with " + polygon.size() + " points, length: " + plotDist);
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
        final ExtrusionPath extrusionPath = new ExtrusionPath(polygon, calculateFeedrate(polygon, extruder));
        plotExtrusionPath(extrusionPath, firstOneInLayer, extruder);
    }

    private double calculateFeedrate(final Polygon polygon, final GCodeExtruder extruder) {
        if (polygon.isClosed()) {
            return preferences.getPrintSettings().getPerimeterSpeed() * extruder.getFastXYFeedrate();
        } else {
            return preferences.getPrintSettings().getInfillSpeed() * extruder.getFastXYFeedrate();
        }
    }

    private void plotExtrusionPath(final ExtrusionPath extrusionPath, final boolean firstOneInLayer,
            final GCodeExtruder extruder) throws IOException {
        final double extrudeBackLength = extruder.getExtrusionOverRun();
        extrusionPath.backStepExtrude(extrudeBackLength);

        final GCodePrinter printer = layerRules.getPrinter();
        final double liftZ = extruder.getLift();
        singleMove(printer, liftZ, extrusionPath.point(0));

        // Print any lead-in.
        printer.startExtruder(firstOneInLayer);
        boolean extrudeOff = false;
        int pathLength = extrusionPath.size();
        if (extrusionPath.isClosed()) {
            // plot to each point(0..n) and then to point(0).
            pathLength++;
        }
        for (int i = 0; i < pathLength; i++) {
            final Point2D point = extrusionPath.point(i % extrusionPath.size());
            final double feedrate = extrusionPath.speed(i % extrusionPath.size());
            final boolean oldexoff = extrudeOff;
            extrudeOff = (i > extrusionPath.extrudeEnd() && extrudeBackLength > 0) || i == pathLength - 1;
            printer.printTo(point.x(), point.y(), layerRules.getMachineZ(), feedrate, extrudeOff);
            if (oldexoff ^ extrudeOff) {
                printer.retract();
            }
        }
        printer.moveTo(printer.getX(), printer.getY(), layerRules.getMachineZ(), printer.getExtruder().getFastXYFeedrate(),
                liftZ > 0);
    }

    private void singleMove(final GCodePrinter printer, final double liftZ, final Point2D point) {
        final double currentZ = printer.getZ();
        final double fastFeedrateZ = printer.getFastFeedrateZ();
        if (liftZ > 0) {
            printer.singleMove(printer.getX(), printer.getY(), currentZ + liftZ, fastFeedrateZ, true);
        }
        printer.singleMove(point.x(), point.y(), currentZ + liftZ, printer.getFastXYFeedrate(), true);
        if (liftZ > 0) {
            printer.singleMove(point.x(), point.y(), currentZ, fastFeedrateZ, true);
        }
    }

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
