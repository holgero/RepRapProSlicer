package org.reprap.geometry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Configuration;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.ExtruderSettings;
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
    private final CurrentConfiguration configuration = Configuration.getInstance().getCurrentConfiguration();
    boolean firstOneInLayer = true;

    LayerProducer(final LayerRules lc, final SimulationPlotter simPlot) {
        layerRules = lc;
        simulationPlot = simPlot;

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

    private void plot(final Polygon polygon) {
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

        if (plotDist < configuration.getPrinterSettings().getMachineResolution() * 0.5) {
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

        final ExtruderSettings extruder = configuration.getExtruderSettings(attributes.getMaterial());
        final ExtrusionPath extrusionPath = new ExtrusionPath(polygon, calculateFeedrate(polygon,
                extruder.getPrintExtrusionRate()));
        plotExtrusionPath(extrusionPath, extruder);
    }

    private double calculateFeedrate(final Polygon polygon, final double extrusionRate) {
        if (polygon.isClosed()) {
            return configuration.getPrintSettings().getPerimeterSpeed() * extrusionRate;
        } else {
            return configuration.getPrintSettings().getInfillSpeed() * extrusionRate;
        }
    }

    private void plotExtrusionPath(final ExtrusionPath extrusionPath, final ExtruderSettings extruder) {
        final double extrudeBackLength = extruder.getExtrusionOverrun();
        extrusionPath.backStepExtrude(extrudeBackLength);

        final GCodePrinter printer = layerRules.getPrinter();
        final double liftZ = extruder.getLift();
        singleMove(printer, liftZ, extrusionPath.point(0));

        // Print any lead-in.
        printer.startExtruder(firstOneInLayer);
        firstOneInLayer = false;
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
        printer.moveTo(printer.getX(), printer.getY(), layerRules.getMachineZ(), extruder.getPrintExtrusionRate(), liftZ > 0);
    }

    private static void singleMove(final GCodePrinter printer, final double liftZ, final Point2D point) {
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

    void plot(final PolygonList pl) {
        for (int j = 0; j < pl.size(); j++) {
            plot(pl.polygon(j));
        }
    }
}
