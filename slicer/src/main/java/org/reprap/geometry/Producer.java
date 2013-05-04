package org.reprap.geometry;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.Preferences;
import org.reprap.configuration.PrintSettings;
import org.reprap.gcode.GCodePrinter;
import org.reprap.gcode.Purge;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.BoundingBox;

public class Producer {
    private static final Logger LOGGER = LogManager.getLogger(Producer.class);
    private LayerRules layerRules = null;
    private SimulationPlotter simulationPlot = null;

    /**
     * The list of objects to be built
     */
    private final ProducerStlList stlList;
    private final ProductionProgressListener progressListener;
    private final GCodePrinter printer;
    private final int totalExtruders;
    /*
     * Skip generating the shield if only one color is printed
     */
    private boolean omitShield;
    private final int brimLines;
    private final boolean printSupport;

    public Producer(final GCodePrinter printer, final AllSTLsToBuild allStls, final ProductionProgressListener listener,
            final boolean displayPaths) {
        this.printer = printer;
        progressListener = listener;
        final Purge purge = new Purge(printer);
        printer.setPurge(purge);
        final BoundingBox buildVolume = ProducerStlList.calculateBoundingBox(allStls, purge);
        layerRules = new LayerRules(printer, buildVolume);
        stlList = new ProducerStlList(allStls, purge, layerRules);
        final CurrentConfiguration configuration = Preferences.getCurrentConfiguration();
        totalExtruders = configuration.getPrinterSettings().getExtruderSettings().size();
        if (displayPaths) {
            simulationPlot = new SimulationPlotter("RepRap building simulation");
        } else {
            simulationPlot = null;
        }
        final PrintSettings printSettings = configuration.getPrintSettings();
        omitShield = printSettings.printShield();
        brimLines = printSettings.getBrimLines();
        printSupport = printSettings.printSupport();
    }

    public void produce() throws IOException {
        while (layerRules.getModelLayer() > 0) {
            produceLayer();
            layerRules.step();
        }
        layerRules.layFoundationTopDown(simulationPlot);
        layerRules.reverseLayers();
    }

    private void produceLayer() throws IOException {
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
        final LayerProducer lp = new LayerProducer(allPolygons, layerRules, simulationPlot);
        lp.plot();
        printer.finishedLayer(false);
    }

    private int usedPhysicalExtruders(final PolygonList[] allPolygons) {
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
        PolygonList fills = stlList.computeInfill(stl);
        final PolygonList borders = stlList.computeOutlines(stl, fills);
        for (int pol = 0; pol < borders.size(); pol++) {
            final Polygon polygon = borders.polygon(pol);
            tempBorderPolygons[getExtruder(polygon)].add(polygon);
        }
        fills = fills.cullShorts(layerRules.getPrinter());
        for (int pol = 0; pol < fills.size(); pol++) {
            final Polygon p = fills.polygon(pol);
            tempFillPolygons[getExtruder(p)].add(p);
        }
        if (printSupport) {
            final PolygonList support = stlList.computeSupport(stl);
            for (int pol = 0; pol < support.size(); pol++) {
                final Polygon p = support.polygon(pol);
                tempFillPolygons[getExtruder(p)].add(p);
            }
        }
        for (int extruder = 0; extruder < totalExtruders; extruder++) {
            if (tempBorderPolygons[extruder].size() > 0) {
                double linkUp = printer.getExtruder(tempBorderPolygons[extruder].polygon(0).getAttributes().getMaterial())
                        .getExtrusionSize();
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
                double linkUp = printer.getExtruder(tempFillPolygons[extruder].polygon(0).getAttributes().getMaterial())
                        .getExtrusionSize();
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

    private int getExtruder(final Polygon polygon) {
        return printer.getExtruder(polygon.getAttributes().getMaterial()).getID();
    }

    public void dispose() {
        if (simulationPlot != null) {
            simulationPlot.dispose();
        }
    }
}
