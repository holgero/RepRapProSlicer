package org.reprap.geometry;

import java.io.IOException;

import org.reprap.attributes.Attributes;
import org.reprap.attributes.Preferences;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.polygons.Interval;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polygons.VelocityProfile;
import org.reprap.utilities.Debug;

class LayerProducer {
    private SimulationPlotter simulationPlot = null;
    private LayerRules layerConditions = null;
    private final PolygonList allPolygons[];
    private double currentFeedrate;

    /**
     * Set up a normal layer
     */
    LayerProducer(final PolygonList ap[], final LayerRules lc, final SimulationPlotter simPlot) throws IOException {
        layerConditions = lc;
        simulationPlot = simPlot;

        allPolygons = ap;

        if (simulationPlot != null) {
            if (!simulationPlot.isInitialised()) {
                final Rectangle rec = lc.getBox();
                if (Preferences.getInstance().loadBool("Shield")) {
                    rec.expand(Point2D.add(rec.sw(), new Point2D(-7, -7))); // TODO: Yuk - this should be a parameter
                }
                simulationPlot.init(rec, "" + lc.getModelLayer() + " (z=" + lc.getModelZ() + ")");
            } else {
                simulationPlot.cleanPolygons("" + lc.getModelLayer() + " (z=" + lc.getModelZ() + ")");
            }
        }
    }

    private Point2D posNow() {
        return new Point2D(layerConditions.getPrinter().getX(), layerConditions.getPrinter().getY());
    }

    /**
     * speed up for short lines
     */
    private boolean shortLine(final Point2D p, final boolean stopExtruder, final boolean closeValve) throws Exception {
        final GCodePrinter printer = layerConditions.getPrinter();
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
        printer.printTo(p.x(), p.y(), layerConditions.getMachineZ(), printer.getExtruder().getShortLineFeedrate(),
                stopExtruder, closeValve);
        return true;
    }

    /**
     * @param first
     *            First point, the end of the line segment to be plotted to from
     *            the current position.
     * @param second
     *            Second point, the end of the next line segment; used for angle
     *            calculations
     * @param turnOff
     *            True if the extruder should be turned off at the end of this
     *            segment.
     * @throws Exception
     */
    private void plot(final Point2D first, final Point2D second, final boolean stopExtruder, final boolean closeValve)
            throws Exception {
        if (shortLine(first, stopExtruder, closeValve)) {
            return;
        }

        final GCodePrinter printer = layerConditions.getPrinter();
        final double z = layerConditions.getMachineZ();

        final double speedUpLength = printer.getExtruder().getAngleSpeedUpLength();
        if (speedUpLength > 0) {
            final SegmentSpeeds ss = SegmentSpeeds.createSegmentSpeeds(posNow(), first, speedUpLength);
            if (ss == null) {
                return;
            }

            printer.printTo(ss.getP1().x(), ss.getP1().y(), z, currentFeedrate, false, false);

            if (ss.isPlotMiddle()) {
                //TODO: FIX THIS.
                //				int straightSpeed = LinePrinter.speedFix(currentSpeed, (1 - 
                //						printer.getExtruder().getAngleSpeedFactor()));
                //printer.setFeedrate(printer.getExtruder().getAngleFeedrate());
                printer.printTo(ss.getP2().x(), ss.getP2().y(), z, printer.getExtruder().getAngleFeedrate(), false, false);
            }

            printer.printTo(ss.getP3().x(), ss.getP3().y(), z, printer.getExtruder().getAngleFeedrate(), stopExtruder,
                    closeValve);
            // Leave speed set for the start of the next line.
        } else {
            printer.printTo(first.x(), first.y(), z, currentFeedrate, stopExtruder, closeValve);
        }
    }

    private void singleMove(final Point2D p) {
        final GCodePrinter pt = layerConditions.getPrinter();
        pt.singleMove(p.x(), p.y(), pt.getZ(), pt.getFastXYFeedrate(), true);
    }

    private void move(final Point2D first, final boolean startUp, final boolean endUp, final boolean fast) throws Exception {
        final GCodePrinter printer = layerConditions.getPrinter();
        final double z = layerConditions.getMachineZ();
        if (fast) {
            printer.moveTo(first.x(), first.y(), z, printer.getExtruder().getFastXYFeedrate(), startUp, endUp);
            return;
        }

        final double speedUpLength = printer.getExtruder().getAngleSpeedUpLength();
        if (speedUpLength > 0) {
            final SegmentSpeeds ss = SegmentSpeeds.createSegmentSpeeds(posNow(), first, speedUpLength);
            if (ss == null) {
                return;
            }

            printer.moveTo(ss.getP1().x(), ss.getP1().y(), z, printer.getCurrentFeedrate(), startUp, startUp);

            if (ss.isPlotMiddle()) {
                printer.moveTo(ss.getP2().x(), ss.getP2().y(), z, currentFeedrate, startUp, startUp);
            }

            //TODO: FIX ME!
            //printer.setSpeed(ss.speed(currentSpeed, printer.getExtruder().getAngleSpeedFactor()));

            //printer.setFeedrate(printer.getExtruder().getAngleFeedrate());
            printer.moveTo(ss.getP3().x(), ss.getP3().y(), z, printer.getExtruder().getAngleFeedrate(), startUp, endUp);
            // Leave speed set for the start of the next movement.
        } else {
            printer.moveTo(first.x(), first.y(), z, currentFeedrate, startUp, endUp);
        }
    }

    /**
     * Plot a polygon
     */
    private void plot(final Polygon polygon, final boolean firstOneInLayer) throws Exception {
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
        if (plotDist < Preferences.getInstance().getMachineResolution() * 0.5) {
            Debug.getInstance().debugMessage("Rejected line with " + polygon.size() + " points, length: " + plotDist);
            return;
        }

        final GCodePrinter printer = layerConditions.getPrinter();
        final double currentZ = printer.getZ();

        if (firstOneInLayer) {
            // The next line tells the printer that it is already at the first point.  It is not, but code will be added just before this
            // to put it there by the LayerRules function that reverses the top-down order of the layers.
            if (Preferences.getInstance().loadBool("RepRapAccelerations")) {
                printer.singleMove(polygon.point(0).x(), polygon.point(0).y(), currentZ, printer.getSlowXYFeedrate(), false);
            } else {
                printer.singleMove(polygon.point(0).x(), polygon.point(0).y(), currentZ, printer.getFastXYFeedrate(), false);
            }
            printer.forceNextExtruder();
        }
        final Attributes attributes = polygon.getAttributes();
        printer.selectExtruder(attributes);

        if (simulationPlot != null) {
            final PolygonList pgl = new PolygonList();
            pgl.add(polygon);
            simulationPlot.add(pgl);
        }

        final GCodeExtruder extruder = attributes.getExtruder();
        final double outlineFeedrate = extruder.getOutlineFeedrate();
        final double infillFeedrate = extruder.getInfillFeedrate();
        final ExtrusionPath extrusionPath = toExtrusionPath(polygon, printer.getFastXYFeedrate(), extruder.getSlowXYFeedrate(),
                polygon.isClosed() ? outlineFeedrate : infillFeedrate, extruder.getMaxAcceleration());

        plotExtrusionPath(extrusionPath, firstOneInLayer, extruder);
    }

    private void plotExtrusionPath(final ExtrusionPath extrusionPath, final boolean firstOneInLayer,
            final GCodeExtruder extruder) throws IOException, Exception {
        final double extrudeBackLength = extruder.getExtrusionOverRun();
        final double valveBackLength = extruder.getValveOverRun();
        if (extrudeBackLength > 0 && valveBackLength > 0) {
            Debug.getInstance().errorMessage(
                    "LayerProducer.plot(): extruder has both valve backoff and extrude backoff specified.");
        }

        extrusionPath.backStepExtrude(extrudeBackLength);
        extrusionPath.backStepValve(valveBackLength);

        final GCodePrinter printer = layerConditions.getPrinter();
        final double currentZ = printer.getZ();
        final double liftZ = extruder.getLift();
        if (liftZ > 0) {
            printer.singleMove(printer.getX(), printer.getY(), currentZ + liftZ, printer.getFastFeedrateZ(), true);
        }

        currentFeedrate = printer.getFastXYFeedrate();
        singleMove(extrusionPath.point(0));

        if (liftZ > 0) {
            printer.singleMove(printer.getX(), printer.getY(), currentZ, printer.getFastFeedrateZ(), true);
        }

        final boolean acc = extruder.getMaxAcceleration() > 0;
        if (acc | (!Preferences.getInstance().loadBool("RepRapAccelerations"))) {
            currentFeedrate = extrusionPath.speed(0);
        } else {
            final double outlineFeedrate = extruder.getOutlineFeedrate();
            final double infillFeedrate = extruder.getInfillFeedrate();
            if (extrusionPath.isClosed()) {
                currentFeedrate = outlineFeedrate;
            } else {
                currentFeedrate = infillFeedrate;
            }
        }

        plot(extrusionPath.point(0), extrusionPath.point(1), false, false);

        // Print any lead-in.
        printer.printStartDelay(firstOneInLayer);

        boolean extrudeOff = false;
        boolean valveOff = false;
        boolean oldexoff;

        final double oldFeedFactor = extruder.getExtrudeRatio();

        for (int i = 1; i < extrusionPath.size(); i++) {
            final Point2D next = extrusionPath.point((i + 1) % extrusionPath.size());
            if (acc) {
                currentFeedrate = extrusionPath.speed(i);
            }
            oldexoff = extrudeOff;
            extrudeOff = (i > extrusionPath.extrudeEnd() && extrudeBackLength > 0) || i == extrusionPath.size() - 1;
            valveOff = (i > extrusionPath.valveEnd() && valveBackLength > 0) || i == extrusionPath.size() - 1;
            plot(extrusionPath.point(i), next, extrudeOff, valveOff);
            if (oldexoff ^ extrudeOff) {
                printer.printEndReverse();
            }
        }

        // Restore sanity
        extruder.setExtrudeRatio(oldFeedFactor);
        if (extrusionPath.isClosed()) {
            move(extrusionPath.point(0), false, false, true);
        }
        // If getMinLiftedZ() is negative, never lift the head
        final boolean lift = extruder.getMinLiftedZ() >= 0 || liftZ > 0;
        move(posNow(), lift, lift, true);
    }

    /**
     * Master plot function - draw everything. Supress border and/or hatch by
     * setting borderPolygons and/or hatchedPolygons null
     */
    void plot() throws Exception {
        boolean firstOneInLayer = true;

        for (final PolygonList pl : allPolygons) {
            for (int j = 0; j < pl.size(); j++) {
                plot(pl.polygon(j), firstOneInLayer);
                firstOneInLayer = false;
            }
        }
    }

    /**
     * Set the speeds at each vertex so that the polygon can be plotted as fast
     * as possible
     * 
     * @return
     */
    public static ExtrusionPath toExtrusionPath(final Polygon polygon, final double airSpeed, final double minSpeed,
            final double maxSpeed, final double acceleration) {
        final boolean reprapAccelerations;
        reprapAccelerations = Preferences.getInstance().loadBool("RepRapAccelerations");

        final ExtrusionPath result = new ExtrusionPath(polygon);

        if (!reprapAccelerations) {
            // If not doing RepRap style accelerations, just move in air to the
            // first point and then go round as fast as possible.
            result.setSpeed(0, airSpeed);
            for (int i = 1; i < result.size(); i++) {
                result.setSpeed(i, maxSpeed);
            }
        } else {
            // RepRap-style accelerations
            final boolean fixup[] = new boolean[result.size()];
            result.setSpeed(0, minSpeed);
            Point2D a, b, c, ab, bc;
            double oldV, vCorner, distance, newS;
            int next;
            a = result.point(0);
            b = result.point(1);
            ab = Point2D.sub(b, a);
            distance = ab.mod();
            ab = Point2D.div(ab, distance);
            oldV = minSpeed;
            fixup[0] = true;
            for (int i = 1; i < result.size(); i++) {
                next = (i + 1) % result.size();
                c = result.point(next);
                bc = Point2D.sub(c, b);
                newS = bc.mod();
                bc = Point2D.div(bc, newS);
                vCorner = Point2D.mul(ab, bc);
                if (vCorner >= 0) {
                    vCorner = minSpeed + (maxSpeed - minSpeed) * vCorner;
                } else {
                    vCorner = 0.5 * minSpeed * (2 + vCorner);
                }

                if (!result.isClosed() && i == result.size() - 1) {
                    vCorner = minSpeed;
                }

                final Interval aRange = ExtrusionPath.accRange(oldV, distance, acceleration);

                if (vCorner <= aRange.low()) {
                    result.backTrack(i, vCorner, acceleration, fixup);
                } else if (vCorner < aRange.high()) {
                    result.setSpeed(i, vCorner);
                    fixup[i] = true;
                } else {
                    result.setSpeed(i, aRange.high());
                    fixup[i] = false;
                }
                b = c;
                ab = bc;
                oldV = result.speed(i);
                distance = newS;
            }

            for (int i = result.isClosed() ? result.size() : result.size() - 1; i > 0; i--) {
                int ib = i;
                if (ib == result.size()) {
                    ib = 0;
                }

                if (fixup[ib]) {
                    final int ia = i - 1;
                    a = result.point(ia);
                    b = result.point(ib);
                    ab = Point2D.sub(b, a);
                    distance = ab.mod();
                    final double va = result.speed(ia);
                    final double vb = result.speed(ib);

                    final VelocityProfile vp = new VelocityProfile(distance, va, maxSpeed, vb, acceleration);
                    switch (vp.flat()) {
                    case 0:
                        break;

                    case 1:
                        result.add(i, Point2D.add(a, Point2D.mul(ab, vp.s1() / distance)), vp.v());
                        break;

                    case 2:
                        result.add(i, Point2D.add(a, Point2D.mul(ab, vp.s2() / distance)), maxSpeed);
                        result.add(i, Point2D.add(a, Point2D.mul(ab, vp.s1() / distance)), maxSpeed);
                        break;

                    default:
                        Debug.getInstance().errorMessage("RrPolygon.setSpeeds(): dud VelocityProfile flat value.");
                    }
                }
            }

            result.validate();
        }
        return result;
    }

}
