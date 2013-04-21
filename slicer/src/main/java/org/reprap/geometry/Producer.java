package org.reprap.geometry;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private final int totalPhysicalExtruders;

    public Producer(final GCodePrinter printer, final AllSTLsToBuild allStls, final ProductionProgressListener listener,
            final boolean displayPaths) throws Exception {
        this.printer = printer;
        progressListener = listener;
        final Purge purge = new Purge(printer);
        printer.setPurge(purge);
        final BoundingBox buildVolume = ProducerStlList.calculateBoundingBox(allStls, purge);
        layerRules = new LayerRules(printer, buildVolume);
        stlList = new ProducerStlList(allStls, purge, layerRules);
        totalPhysicalExtruders = countPhysicalExtruders();
        if (displayPaths) {
            simulationPlot = new SimulationPlotter("RepRap building simulation");
        } else {
            simulationPlot = null;
        }
    }

    public void produce() throws Exception {
        layerRules.setLayingSupport(false);
        while (layerRules.getModelLayer() > 0) {
            produceLayer();
            layerRules.step();
        }
        layerRules.layFoundationTopDown(simulationPlot);
        layerRules.reverseLayers();
    }

    private void produceLayer() throws IOException {
        LOGGER.debug("Commencing model layer " + layerRules.getModelLayer() + " at " + layerRules.getMachineZ());
        if (layerRules.getModelLayer() == 0) {
            printer.setSeparating(true);
        } else {
            printer.setSeparating(false);
        }
        layerRules.setLayerFileName(printer.startingLayer(layerRules.getZStep(), layerRules.getMachineZ(),
                layerRules.getMachineLayer(), layerRules.getMachineLayerMax(), false));
        progressListener.productionProgress(layerRules.getMachineLayer(), layerRules.getMachineLayerMax());

        final PolygonList allPolygons[] = new PolygonList[totalPhysicalExtruders];
        for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
            allPolygons[physicalExtruder] = new PolygonList();
        }

        Point2D startNearHere = new Point2D(100, 100); // TODO make this the middle of the printer bed
        for (int stl = 1; stl < stlList.size(); stl++) {
            startNearHere = collectPolygonsForObject(stl, startNearHere, allPolygons);
        }

        if (usedPhysicalExtruders(allPolygons) > 1) {
            // TODO for now we redo the calculation to get a similar result as in the previous implementation, this should be avoided
            for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
                allPolygons[physicalExtruder] = new PolygonList();
            }
            startNearHere = new Point2D(0, 0);
            for (int stl = 0; stl < stlList.size(); stl++) {
                startNearHere = collectPolygonsForObject(stl, startNearHere, allPolygons);
            }
        }

        layerRules.setFirstAndLast(allPolygons);
        final LayerProducer lp = new LayerProducer(allPolygons, layerRules, simulationPlot);
        lp.plot();
        printer.finishedLayer(layerRules.getReversing());
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
        final PolygonList tempBorderPolygons[] = new PolygonList[totalPhysicalExtruders];
        final PolygonList tempFillPolygons[] = new PolygonList[totalPhysicalExtruders];
        for (int physicalExtruder = 0; physicalExtruder < totalPhysicalExtruders; physicalExtruder++) {
            tempBorderPolygons[physicalExtruder] = new PolygonList();
            tempFillPolygons[physicalExtruder] = new PolygonList();
        }
        PolygonList fills = stlList.computeInfill(stl);
        final PolygonList borders = stlList.computeOutlines(stl, fills);
        for (int pol = 0; pol < borders.size(); pol++) {
            final Polygon polygon = borders.polygon(pol);
            tempBorderPolygons[getPhysicalExtruder(polygon)].add(polygon);
        }
        fills = fills.cullShorts(layerRules.getPrinter());
        for (int pol = 0; pol < fills.size(); pol++) {
            final Polygon p = fills.polygon(pol);
            tempFillPolygons[getPhysicalExtruder(p)].add(p);
        }
        final PolygonList support = stlList.computeSupport(stl);
        for (int pol = 0; pol < support.size(); pol++) {
            final Polygon p = support.polygon(pol);
            tempFillPolygons[getPhysicalExtruder(p)].add(p);
        }

        for (int physicalExtruder = 0; physicalExtruder < totalPhysicalExtruders; physicalExtruder++) {
            if (tempBorderPolygons[physicalExtruder].size() > 0) {
                double linkUp = printer.getExtruder(
                        tempBorderPolygons[physicalExtruder].polygon(0).getAttributes().getMaterial()).getExtrusionSize();
                linkUp = (4 * linkUp * linkUp);
                tempBorderPolygons[physicalExtruder].radicalReOrder(linkUp, printer);
                tempBorderPolygons[physicalExtruder] = tempBorderPolygons[physicalExtruder].nearEnds(startNearHere, false, -1);
                if (tempBorderPolygons[physicalExtruder].size() > 0) {
                    final Polygon last = tempBorderPolygons[physicalExtruder].polygon(tempBorderPolygons[physicalExtruder]
                            .size() - 1);
                    startNearHere = last.point(last.size() - 1);
                }
                allPolygons[physicalExtruder].add(tempBorderPolygons[physicalExtruder]);
            }
            if (tempFillPolygons[physicalExtruder].size() > 0) {
                tempFillPolygons[physicalExtruder].polygon(0).getAttributes();
                double linkUp = printer
                        .getExtruder(tempFillPolygons[physicalExtruder].polygon(0).getAttributes().getMaterial())
                        .getExtrusionSize();
                linkUp = (4 * linkUp * linkUp);
                tempFillPolygons[physicalExtruder].radicalReOrder(linkUp, printer);
                tempFillPolygons[physicalExtruder] = tempFillPolygons[physicalExtruder].nearEnds(startNearHere, false, -1);
                if (tempFillPolygons[physicalExtruder].size() > 0) {
                    final Polygon last = tempFillPolygons[physicalExtruder]
                            .polygon(tempFillPolygons[physicalExtruder].size() - 1);
                    startNearHere = last.point(last.size() - 1);
                }
                allPolygons[physicalExtruder].add(tempFillPolygons[physicalExtruder]);
            }
        }
        return startNearHere;
    }

    private int countPhysicalExtruders() {
        int result = 0;
        int lastExtruder = -1;
        for (int extruder = 0; extruder < printer.getExtruders().length; extruder++) {
            final int thisExtruder = printer.getExtruders()[extruder].getPhysicalExtruderNumber();
            if (thisExtruder > lastExtruder) {
                result++;
                if (thisExtruder - lastExtruder != 1) {
                    LOGGER.fatal("Producer.produceAdditiveTopDown(): Physical extruders out of sequence: " + lastExtruder
                            + " then " + thisExtruder);
                    throw new RuntimeException("Extruder addresses must be monotonically increasing starting at 0.");
                }
                lastExtruder = thisExtruder;
            }
        }
        return result;
    }

    private int getPhysicalExtruder(final Polygon polygon) {
        return printer.getExtruder(polygon.getAttributes().getMaterial()).getPhysicalExtruderNumber();
    }

    public void dispose() {
        if (simulationPlot != null) {
            simulationPlot.dispose();
        }
    }
}
