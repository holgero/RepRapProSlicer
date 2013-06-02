package org.reprap.geometry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.ExtruderSetting;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.grids.BooleanGrid;
import org.reprap.geometry.grids.Hatcher;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;

class LayerProducer {
    private static final Logger LOGGER = LogManager.getLogger(LayerProducer.class);
    private final SimulationPlotter simulationPlot;
    private final LayerRules layerRules;
    private final CurrentConfiguration currentConfiguration;
    boolean firstOneInLayer = true;
    private final GCodePrinter printer;

    LayerProducer(final LayerRules lc, final SimulationPlotter simPlot, final CurrentConfiguration currentConfiguration,
            final GCodePrinter printer) {
        layerRules = lc;
        simulationPlot = simPlot;
        this.currentConfiguration = currentConfiguration;
        this.printer = printer;

        if (simulationPlot != null) {
            if (!simulationPlot.isInitialised()) {
                final Rectangle rec = new Rectangle(lc.getBox());
                simulationPlot.init(rec);
            } else {
                try {
                    while (simulationPlot.isPauseSlicer()) {
                        Thread.sleep(1000);
                    }
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                }
                simulationPlot.cleanPolygons();
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

        if (plotDist < currentConfiguration.getPrinterSetting().getMachineResolution() * 0.5) {
            LOGGER.info("Rejected line with " + polygon.size() + " points, length: " + plotDist);
            return;
        }

        final double currentZ = printer.getZ();

        if (firstOneInLayer) {
            printer.moveTo(polygon.point(0).x(), polygon.point(0).y(), currentZ, printer.getFastXYFeedrate(), false);
        }
        final String material = polygon.getMaterial();
        printer.selectExtruder(material);

        if (simulationPlot != null) {
            final PolygonList pgl = new PolygonList();
            pgl.add(polygon);
            simulationPlot.add(pgl);
        }

        final ExtruderSetting extruder = currentConfiguration.getExtruderSetting(material);
        final ExtrusionPath extrusionPath = new ExtrusionPath(polygon, calculateFeedrate(polygon,
                extruder.getPrintExtrusionRate()));
        plotExtrusionPath(extrusionPath, extruder);
    }

    private double calculateFeedrate(final Polygon polygon, final double extrusionRate) {
        if (polygon.isClosed()) {
            return currentConfiguration.getPrintSetting().getPerimeterSpeed() * extrusionRate;
        } else {
            return currentConfiguration.getPrintSetting().getInfillSpeed() * extrusionRate;
        }
    }

    private void plotExtrusionPath(final ExtrusionPath extrusionPath, final ExtruderSetting extruder) {
        final double extrudeBackLength = extruder.getExtrusionOverrun();
        extrusionPath.backStepExtrude(extrudeBackLength);

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
            printer.moveTo(point.x(), point.y(), layerRules.getMachineZ(), feedrate, false);
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
            printer.moveTo(printer.getX(), printer.getY(), currentZ + liftZ, fastFeedrateZ, false);
        }
        printer.moveTo(point.x(), point.y(), currentZ + liftZ, printer.getFastXYFeedrate(), false);
        if (liftZ > 0) {
            printer.moveTo(point.x(), point.y(), currentZ, fastFeedrateZ, false);
        }
    }

    void plot(final PolygonList pl) {
        for (int j = 0; j < pl.size(); j++) {
            plot(pl.polygon(j));
        }
    }

    void layFoundationBottomUp() {
        if (layerRules.getFoundationLayers() <= 0) {
            return;
        }
        while (layerRules.getMachineLayer() < layerRules.getFoundationLayers()) {
            LOGGER.debug("Commencing foundation layer at " + layerRules.getMachineZ());
            printer.startingLayer(layerRules.getZStep(), layerRules.getMachineZ(), layerRules.getMachineLayer(),
                    layerRules.getMachineLayerMax());
            fillFoundationRectangle();
            layerRules.stepMachine();
        }
    }

    private void fillFoundationRectangle() {
        final int supportExtruderNo = currentConfiguration.getPrintSetting().getSupportExtruder();
        final ExtruderSetting supportExtruder = currentConfiguration.getPrinterSetting().getExtruderSettings()
                .get(supportExtruderNo);
        final double extrusionSize = supportExtruder.getExtrusionSize();
        final String supportMaterial = currentConfiguration.getMaterials().get(supportExtruderNo).getName();
        final Rectangle box = layerRules.getBox();
        final Hatcher hatcher = new Hatcher(new BooleanGrid(
                currentConfiguration.getPrinterSetting().getMachineResolution() * 0.6, supportMaterial, box.scale(1.1),
                CSG2D.RrCSGFromBox(box)));
        final PolygonList foundationPolygon = hatcher.hatch(layerRules.getFillHatchLine(extrusionSize), extrusionSize,
                currentConfiguration.getPrintSetting().isPathOptimize());
        layerRules.setFirstAndLast(new PolygonList[] { foundationPolygon });
        plot(foundationPolygon);
    }
}
