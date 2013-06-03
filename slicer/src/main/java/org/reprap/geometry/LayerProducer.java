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
            printer.moveTo(polygon.point(0).x(), polygon.point(0).y(), currentZ, printer.getFastXYFeedrate());
        }
        final String material = polygon.getMaterial();
        printer.selectExtruder(material);

        if (simulationPlot != null) {
            simulationPlot.add(polygon);
        }

        final ExtruderSetting extruder = currentConfiguration.getExtruderSetting(material);
        final ExtrusionPath extrusionPath = new ExtrusionPath(polygon);
        plotExtrusionPath(extrusionPath, extruder);
    }

    private double calculateFeedrate(final ExtrusionPath path, final double extrusionRate) {
        if (path.isClosed()) {
            return currentConfiguration.getPrintSetting().getPerimeterSpeed() * extrusionRate;
        } else {
            return currentConfiguration.getPrintSetting().getInfillSpeed() * extrusionRate;
        }
    }

    private void plotExtrusionPath(final ExtrusionPath extrusionPath, final ExtruderSetting extruder) {
        final double extrudeBackLength = extruder.getExtrusionOverrun();
        extrusionPath.backStepExtrude(extrudeBackLength);

        final Point2D startPoint = extrusionPath.point(0);
        printer.travelTo(startPoint.x(), startPoint.y());

        // Print any lead-in.
        printer.startExtruder(firstOneInLayer);
        firstOneInLayer = false;

        final double feedrate = calculateFeedrate(extrusionPath, extruder.getPrintExtrusionRate());
        int pathLength = extrusionPath.size();
        if (extrusionPath.isClosed()) {
            // plot to each point(0..n) and then to point(0).
            pathLength++;
        }
        int extruderOffIndex = pathLength - 1;
        if (extrudeBackLength > 0) {
            extruderOffIndex = extrusionPath.extrudeEnd() + 1;
        }
        for (int i = 0; i < pathLength; i++) {
            final Point2D point = extrusionPath.point(i % extrusionPath.size());
            printer.moveTo(point.x(), point.y(), layerRules.getMachineZ(), feedrate);
            if (i == extruderOffIndex) {
                printer.retract();
            }
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
        plot(foundationPolygon);
    }
}
