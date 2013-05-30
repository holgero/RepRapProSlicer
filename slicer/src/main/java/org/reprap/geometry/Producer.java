package org.reprap.geometry;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.ExtruderSetting;
import org.reprap.configuration.PrintSetting;
import org.reprap.gcode.GCodePrinter;
import org.reprap.gcode.Purge;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polyhedra.STLObject;

public class Producer {
    private static final Logger LOGGER = LogManager.getLogger(Producer.class);
    private final LayerRules layerRules;
    private final SimulationPlotter simulationPlot;
    /**
     * The list of objects to be built
     */
    private final ProducerStlList stlList;
    private final PolygonList[] support;
    private final ProductionProgressListener progressListener;
    private final GCodePrinter printer;
    private final int totalExtruders;
    private final int brimLines;
    private final CurrentConfiguration currentConfiguration;
    private final InFillPatterns inFillPatterns;

    /*
     * Skip generating the shield if only one color is printed
     */
    private boolean omitShield;

    public Producer(final File gcodeFile, final List<STLObject> stlObjects, final ProductionProgressListener progressListener,
            final SimulationPlotter simulationPlot, final CurrentConfiguration currentConfiguration) {
        this.progressListener = progressListener;
        this.simulationPlot = simulationPlot;
        this.currentConfiguration = currentConfiguration;
        final Purge purge = new Purge(currentConfiguration);
        printer = new GCodePrinter(currentConfiguration, purge);
        if (gcodeFile != null) {
            printer.setGCodeFileForOutput(gcodeFile);
        }
        if (currentConfiguration.getPrintSetting().printShield()) {
            final BoundingBox boxWithoutShield = ProducerStlList.getBoundingBox(stlObjects);
            final double modelZMax = boxWithoutShield.getZint().high();
            stlObjects.add(0, purge.getShield(modelZMax));
        }
        final BoundingBox buildVolume = ProducerStlList.getBoundingBox(stlObjects);
        layerRules = new LayerRules(printer, buildVolume, currentConfiguration);
        stlList = new ProducerStlList(stlObjects, layerRules, currentConfiguration);
        inFillPatterns = new InFillPatterns(layerRules, currentConfiguration);
        totalExtruders = currentConfiguration.getPrinterSetting().getExtruderSettings().size();
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        omitShield = printSetting.printShield();
        brimLines = printSetting.getBrimLines();
        if (printSetting.printSupport()) {
            support = new PolygonList[layerRules.getMachineLayerMax() + 1];
        } else {
            support = new PolygonList[0];
        }
    }

    public void produce() {
        if (currentConfiguration.getPrintSetting().printSupport()) {
            calculateSupportPolygons();
        }
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

    private void calculateSupportPolygons() {
        final SupportCalculator supportCalculator = new SupportCalculator(currentConfiguration, layerRules.sliceCacheSize(),
                stlList.size());
        for (int layer = layerRules.getMachineLayerMax(); layer > 0; layer--) {
            for (int stl = 1; stl < stlList.size(); stl++) {
                final Slice slice = stlList.slice(stl, layer);
                support[layer] = supportCalculator.computeSupport(stl, slice, layer);
            }
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
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        final List<ExtruderSetting> extruderSettings = currentConfiguration.getPrinterSetting().getExtruderSettings();
        final Slice slice = stlList.slice(stl, layerRules.getModelLayer());
        if (layerRules.getModelLayer() == 1 && brimLines > 0) {
            final double extrusionSize = extruderSettings.get(0).getExtrusionSize();
            final PolygonList brim = slice.computeBrim(brimLines, extrusionSize);
            final double linkUp = 4 * extrusionSize * extrusionSize;
            startNearHere = simplifyAndAdd(brim, linkUp, startNearHere, allPolygons[0]);
        }
        for (int extruder = 0; extruder < totalExtruders; extruder++) {
            final PolygonList result = allPolygons[extruder];
            final String material = currentConfiguration.getMaterials().get(extruder).getName();
            final double extrusionSize = extruderSettings.get(extruder).getExtrusionSize();
            final double linkUp = 4 * extrusionSize * extrusionSize;
            final boolean insideOut = printSetting.isInsideOut();
            final int shells = printSetting.getVerticalShells();
            if (printSetting.printSupport() && extruder == printSetting.getSupportExtruder()) {
                startNearHere = simplifyAndAdd(support[layerRules.getModelLayer()], linkUp, startNearHere, result);
            }
            final PolygonList borders = slice.getOutlineGrids(material, shells, extrusionSize, insideOut);
            final PolygonList fills = inFillPatterns.computePolygonsForMaterial(stl, slice, stlList, material, borders);
            startNearHere = simplifyAndAdd(borders, linkUp, startNearHere, result);
            startNearHere = simplifyAndAdd(fills, linkUp, startNearHere, result);
        }
        return startNearHere;
    }

    private static Point2D simplifyAndAdd(final PolygonList list, final double linkUp, Point2D startNearHere,
            final PolygonList result) {
        if (list.size() > 0) {
            list.radicalReOrder(linkUp);
            final PolygonList reorderedList = list.nearEnds(startNearHere);
            final Polygon last = reorderedList.polygon(reorderedList.size() - 1);
            startNearHere = last.point(last.size() - 1);
            result.add(reorderedList);
        }
        return startNearHere;
    }
}
