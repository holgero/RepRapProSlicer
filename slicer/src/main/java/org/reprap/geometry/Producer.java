package org.reprap.geometry;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Configuration;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.ExtruderSetting;
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
    private final CurrentConfiguration configuration;
    /*
     * Skip generating the shield if only one color is printed
     */
    private boolean omitShield;

    public Producer(final GCodePrinter printer, final AllSTLsToBuild allStls, final ProductionProgressListener listener,
            final boolean displayPaths) {
        this.printer = printer;
        progressListener = listener;
        final Purge purge = new Purge();
        printer.setPurge(purge);
        final BoundingBox buildVolume = ProducerStlList.calculateBoundingBox(allStls, purge);
        layerRules = new LayerRules(printer, buildVolume);
        stlList = new ProducerStlList(allStls, purge, layerRules);
        configuration = Configuration.getInstance().getCurrentConfiguration();
        totalExtruders = configuration.getPrinterSetting().getExtruderSettings().size();
        if (displayPaths) {
            simulationPlot = new SimulationPlotter("RepRap building simulation");
        } else {
            simulationPlot = null;
        }
        final PrintSetting printSetting = configuration.getPrintSetting();
        omitShield = printSetting.printShield();
        brimLines = printSetting.getBrimLines();
        printSupport = printSetting.printSupport();
    }

    public void produce() throws IOException {
        while (layerRules.getModelLayer() > 0) {
            produceLayer();
            layerRules.step();
        }
        layerRules.layFoundationTopDown(simulationPlot);
        layerRules.reverseLayers();
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
        layerRules.setFirstAndLast(allPolygons);
        final LayerProducer lp = new LayerProducer(layerRules, simulationPlot);
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
        final PolygonList tempBorderPolygons[] = new PolygonList[totalExtruders];
        final PolygonList tempFillPolygons[] = new PolygonList[totalExtruders];
        for (int extruder = 0; extruder < totalExtruders; extruder++) {
            tempBorderPolygons[extruder] = new PolygonList();
            tempFillPolygons[extruder] = new PolygonList();
        }
        if (layerRules.getModelLayer() == 1 && brimLines > 0) {
            final PolygonList brim = stlList.computeBrim(stl, brimLines);
            tempBorderPolygons[0].add(brim);
        }
        final PolygonList fills = stlList.computeInfill(stl);
        final PolygonList borders = stlList.computeOutlines(stl, fills);
        for (int pol = 0; pol < borders.size(); pol++) {
            final Polygon polygon = borders.polygon(pol);
            tempBorderPolygons[getExtruderId(polygon)].add(polygon);
        }
        for (int pol = 0; pol < fills.size(); pol++) {
            final Polygon polygon = fills.polygon(pol);
            final double minLength = 3 * configuration.getExtruderSetting(polygon.getAttributes().getMaterial())
                    .getExtrusionSize();
            if (polygon.getLength() > minLength) {
                tempFillPolygons[getExtruderId(polygon)].add(polygon);
            }
        }
        if (printSupport) {
            final PolygonList support = stlList.computeSupport(stl);
            for (int pol = 0; pol < support.size(); pol++) {
                final Polygon polygon = support.polygon(pol);
                tempFillPolygons[getExtruderId(polygon)].add(polygon);
            }
        }
        for (int extruder = 0; extruder < totalExtruders; extruder++) {
            if (tempBorderPolygons[extruder].size() > 0) {
                double linkUp = configuration.getExtruderSetting(
                        tempBorderPolygons[extruder].polygon(0).getAttributes().getMaterial()).getExtrusionSize();
                linkUp = (4 * linkUp * linkUp);
                tempBorderPolygons[extruder].radicalReOrder(linkUp);
                tempBorderPolygons[extruder] = tempBorderPolygons[extruder].nearEnds(startNearHere);
                if (tempBorderPolygons[extruder].size() > 0) {
                    final Polygon last = tempBorderPolygons[extruder].polygon(tempBorderPolygons[extruder].size() - 1);
                    startNearHere = last.point(last.size() - 1);
                }
                allPolygons[extruder].add(tempBorderPolygons[extruder]);
            }
            if (tempFillPolygons[extruder].size() > 0) {
                tempFillPolygons[extruder].polygon(0).getAttributes();
                double linkUp = configuration.getExtruderSetting(
                        tempFillPolygons[extruder].polygon(0).getAttributes().getMaterial()).getExtrusionSize();
                linkUp = (4 * linkUp * linkUp);
                tempFillPolygons[extruder].radicalReOrder(linkUp);
                tempFillPolygons[extruder] = tempFillPolygons[extruder].nearEnds(startNearHere);
                if (tempFillPolygons[extruder].size() > 0) {
                    final Polygon last = tempFillPolygons[extruder].polygon(tempFillPolygons[extruder].size() - 1);
                    startNearHere = last.point(last.size() - 1);
                }
                allPolygons[extruder].add(tempFillPolygons[extruder]);
            }
        }
        return startNearHere;
    }

    private int getExtruderId(final Polygon polygon) {
        final List<ExtruderSetting> extruderSettings = configuration.getPrinterSetting().getExtruderSettings();
        for (int i = 0; i < extruderSettings.size(); i++) {
            final ExtruderSetting setting = extruderSettings.get(i);
            if (setting.getMaterial().getName().equals(polygon.getAttributes().getMaterial())) {
                return i;
            }
        }
        return 0;
    }

    public void dispose() {
        if (simulationPlot != null) {
            simulationPlot.dispose();
        }
    }
}
