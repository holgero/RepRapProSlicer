package org.reprap.geometry;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.PrintSetting;
import org.reprap.gcode.GCodePrinter;
import org.reprap.gcode.Purge;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;

public class Producer {
    private static final Logger LOGGER = LogManager.getLogger(Producer.class);
    private final LayerRules layerRules;
    private final SimulationPlotter simulationPlot;
    /**
     * The list of objects to be built
     */
    private final ProducerStlList stlList;
    private final ProductionProgressListener progressListener;
    private final GCodePrinter printer;
    private final int totalExtruders;
    private final int brimLines;
    private final boolean printSupport;
    private final CurrentConfiguration currentConfiguration;
    private final InFillPatterns inFillPatterns;

    /*
     * Skip generating the shield if only one color is printed
     */
    private boolean omitShield;

    public Producer(final File gcodeFile, final AllSTLsToBuild allStls, final ProductionProgressListener progressListener,
            final SimulationPlotter simulationPlot, final CurrentConfiguration currentConfiguration) {
        this.progressListener = progressListener;
        this.simulationPlot = simulationPlot;
        this.currentConfiguration = currentConfiguration;
        final Purge purge = new Purge(currentConfiguration);
        printer = new GCodePrinter(currentConfiguration, purge);
        if (gcodeFile != null) {
            printer.setGCodeFileForOutput(gcodeFile);
        }
        final BoundingBox buildVolume = ProducerStlList.calculateBoundingBox(allStls, purge, currentConfiguration);
        layerRules = new LayerRules(printer, buildVolume, currentConfiguration);
        stlList = new ProducerStlList(allStls, purge, layerRules, currentConfiguration);
        inFillPatterns = new InFillPatterns(layerRules, currentConfiguration);
        totalExtruders = currentConfiguration.getPrinterSetting().getExtruderSettings().size();
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        omitShield = printSetting.printShield();
        brimLines = printSetting.getBrimLines();
        printSupport = printSetting.printSupport();
    }

    public void produce() {
        while (layerRules.getModelLayer() > 0) {
            produceLayer();
            layerRules.step();
        }
        layerRules.layFoundationTopDown(simulationPlot);
        try {
            layerRules.reverseLayers();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void produceLayer() {
        LOGGER.debug("Commencing model layer " + layerRules.getModelLayer() + " at " + layerRules.getMachineZ());
        layerRules.setLayerFileName(printer.startingLayer(layerRules.getZStep(), layerRules.getMachineZ(),
                layerRules.getMachineLayer(), layerRules.getMachineLayerMax(), false));
        progressListener.productionProgress(layerRules.getMachineLayer(), layerRules.getMachineLayerMax());

        final PolygonList allPolygons[] = new PolygonList[totalExtruders];

        Point2D startNearHere = Point2D.mul(layerRules.getPrinter().getBedNorthEast(), 0.5);
        if (omitShield) {
            for (int extruder = 0; extruder < allPolygons.length; extruder++) {
                allPolygons[extruder] = new PolygonList();
            }
            for (int stl = 1; stl < stlList.size(); stl++) {
                startNearHere = collectPolygonsForObject(stl, startNearHere, allPolygons);
            }
            if (usedPhysicalExtruders(allPolygons) > 1) {
                omitShield = false;
            }
        }
        if (!omitShield) {
            for (int extruder = 0; extruder < allPolygons.length; extruder++) {
                allPolygons[extruder] = new PolygonList();
            }
            startNearHere = new Point2D(0, 0);
            for (int stl = 0; stl < stlList.size(); stl++) {
                startNearHere = collectPolygonsForObject(stl, startNearHere, allPolygons);
            }
        }
        progressListener.productionProgress(layerRules.getMachineLayer(), layerRules.getMachineLayerMax());
        layerRules.setFirstAndLast(allPolygons);
        final LayerProducer lp = new LayerProducer(layerRules, simulationPlot, currentConfiguration);
        for (final PolygonList pl : allPolygons) {
            lp.plot(pl);
        }
        printer.finishedLayer(false);
    }

    private static int usedPhysicalExtruders(final PolygonList[] allPolygons) {
        int count = 0;
        for (final PolygonList polygonList : allPolygons) {
            if (polygonList.size() > 0) {
                count++;
            }
        }
        return count;
    }

    private Point2D collectPolygonsForObject(final int stl, Point2D startNearHere, final PolygonList[] allPolygons) {
        for (int extruder = 0; extruder < totalExtruders; extruder++) {
            final String material = currentConfiguration.getMaterials().get(extruder).getName();
            PolygonList fills = inFillPatterns.computePolygonsForMaterial(stl, stlList, material);
            if (printSupport && extruder == currentConfiguration.getPrintSetting().getSupportExtruder()) {
                final PolygonList support = stlList.computeSupport(stl);
                for (int pol = 0; pol < support.size(); pol++) {
                    final Polygon polygon = support.polygon(pol);
                    fills.add(polygon);
                }
            }
            PolygonList borders = stlList.computeOutlines(stl, fills, material);
            if (layerRules.getModelLayer() == 1 && brimLines > 0 && extruder == 0) {
                final PolygonList brim = stlList.computeBrim(stl, brimLines);
                borders.add(brim);
            }
            if (borders.size() > 0) {
                final double linkUp = currentConfiguration.getExtruderSetting(borders.polygon(0).getMaterial())
                        .getExtrusionSize();
                borders.radicalReOrder(4 * linkUp * linkUp);
                borders = borders.nearEnds(startNearHere);
                if (borders.size() > 0) {
                    final Polygon last = borders.polygon(borders.size() - 1);
                    startNearHere = last.point(last.size() - 1);
                }
                allPolygons[extruder].add(borders);
            }
            if (fills.size() > 0) {
                final double linkUp = currentConfiguration.getExtruderSetting(fills.polygon(0).getMaterial())
                        .getExtrusionSize();
                fills.radicalReOrder(4 * linkUp * linkUp);
                fills = fills.nearEnds(startNearHere);
                if (fills.size() > 0) {
                    final Polygon last = fills.polygon(fills.size() - 1);
                    startNearHere = last.point(last.size() - 1);
                }
                allPolygons[extruder].add(fills);
            }
        }
        return startNearHere;
    }
}
