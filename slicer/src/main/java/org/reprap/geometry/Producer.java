package org.reprap.geometry;

import java.io.File;
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
import org.reprap.geometry.polyhedra.BoundingBox;
import org.reprap.geometry.polyhedra.STLObject;

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
    private final CurrentConfiguration currentConfiguration;
    private final InFillPatterns inFillPatterns;
    private final SupportCalculator supportCalculator;

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
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        if (printSetting.printShield()) {
            final double modelZMax = ProducerStlList.getMaxMultimaterialZ(stlObjects);
            if (modelZMax > 0) {
                stlObjects.add(0, purge.getShield(modelZMax));
            }
        }
        final BoundingBox buildVolume = ProducerStlList.getBoundingBox(stlObjects);
        layerRules = new LayerRules(buildVolume, currentConfiguration);
        stlList = new ProducerStlList(stlObjects, layerRules, currentConfiguration);
        inFillPatterns = new InFillPatterns(layerRules, currentConfiguration);
        totalExtruders = currentConfiguration.getPrinterSetting().getExtruderSettings().size();
        brimLines = printSetting.getBrimLines();
        supportCalculator = new SupportCalculator(currentConfiguration, stlList.size(), layerRules.getMachineLayerMax() + 1);
    }

    public void produce() {
        if (currentConfiguration.getPrintSetting().printSupport()) {
            supportCalculator.calculateSupportPolygons(layerRules, stlList);
        }
        startPrint();
        new LayerProducer(layerRules, simulationPlot, currentConfiguration, printer).layFoundationBottomUp();
        while (layerRules.getMachineLayer() < layerRules.getMachineLayerMax()) {
            produceLayer();
            layerRules.step();
        }
        printer.terminate();
    }

    void startPrint() {
        // Sets current X, Y, Z to 0 and optionally plots an outline
        printer.startRun(layerRules.getBox(), layerRules.getZStep(), layerRules.getMachineLayerMax() * layerRules.getZStep());
    }

    private void produceLayer() {
        progressListener.productionProgress(layerRules.getMachineLayer(), layerRules.getMachineLayerMax());

        final PolygonList allPolygons[] = new PolygonList[totalExtruders];
        for (int extruder = 0; extruder < allPolygons.length; extruder++) {
            allPolygons[extruder] = new PolygonList();
        }
        Point2D startNearHere = new Point2D(0, 0);
        for (int stl = 0; stl < stlList.size(); stl++) {
            startNearHere = collectPolygonsForObject(stl, startNearHere, allPolygons);
        }
        if (layerRules.setFirstAndLast(allPolygons)) {
            LOGGER.debug("Commencing model layer " + layerRules.getModelLayer() + " at " + layerRules.getMachineZ());
            printer.startingLayer(layerRules.getZStep(), layerRules.getMachineZ(), layerRules.getMachineLayer(),
                    layerRules.getMachineLayerMax());
            final LayerProducer lp = new LayerProducer(layerRules, simulationPlot, currentConfiguration, printer);
            for (final PolygonList pl : allPolygons) {
                lp.plot(pl);
            }
        } else {
            LOGGER.info("Empty layer " + layerRules.getMachineLayer());
        }
    }

    private Point2D collectPolygonsForObject(final int stl, Point2D startNearHere, final PolygonList[] allPolygons) {
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        final List<ExtruderSetting> extruderSettings = currentConfiguration.getPrinterSetting().getExtruderSettings();
        final Slice slice = stlList.slice(stl, layerRules.getModelLayer());
        if (layerRules.getModelLayer() == 0 && brimLines > 0) {
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
                final PolygonList support = supportCalculator.getSupport(layerRules.getModelLayer());
                if (support != null) {
                    startNearHere = simplifyAndAdd(support, linkUp, startNearHere, result);
                }
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
