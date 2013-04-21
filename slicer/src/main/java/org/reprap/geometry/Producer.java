package org.reprap.geometry;

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

    public Producer(final GCodePrinter printer, final AllSTLsToBuild allStls, final ProductionProgressListener listener,
            final boolean displayPaths) throws Exception {
        progressListener = listener;
        final Purge purge = new Purge(printer);
        printer.setPurge(purge);
        final BoundingBox buildVolume = ProducerStlList.calculateBoundingBox(allStls, purge);
        layerRules = new LayerRules(printer, buildVolume);
        stlList = new ProducerStlList(allStls, purge, layerRules);

        if (displayPaths) {
            simulationPlot = new SimulationPlotter("RepRap building simulation");
        } else {
            simulationPlot = null;
        }
    }

    public void produce() throws Exception {
        final GCodePrinter printer = layerRules.getPrinter();
        layerRules.setLayingSupport(false);
        int lastExtruder = -1;
        int totalPhysicalExtruders = 0;
        for (int extruder = 0; extruder < printer.getExtruders().length; extruder++) {
            final int thisExtruder = printer.getExtruders()[extruder].getPhysicalExtruderNumber();
            if (thisExtruder > lastExtruder) {
                totalPhysicalExtruders++;
                if (thisExtruder - lastExtruder != 1) {
                    LOGGER.error("Producer.produceAdditiveTopDown(): Physical extruders out of sequence: " + lastExtruder
                            + " then " + thisExtruder);
                    LOGGER.error("(Extruder addresses should be monotonically increasing starting at 0.)");
                }
                lastExtruder = thisExtruder;
            }
        }

        final PolygonList allPolygons[] = new PolygonList[totalPhysicalExtruders];
        final PolygonList tempBorderPolygons[] = new PolygonList[totalPhysicalExtruders];
        final PolygonList tempFillPolygons[] = new PolygonList[totalPhysicalExtruders];

        while (layerRules.getModelLayer() > 0) {
            if (layerRules.getModelLayer() == 0) {
                printer.setSeparating(true);
            } else {
                printer.setSeparating(false);
            }

            LOGGER.debug("Commencing model layer " + layerRules.getModelLayer() + " at " + layerRules.getMachineZ());
            layerRules.setLayerFileName(printer.startingLayer(layerRules.getZStep(), layerRules.getMachineZ(),
                    layerRules.getMachineLayer(), layerRules.getMachineLayerMax(), false));
            progressListener.productionProgress(layerRules.getMachineLayer(), layerRules.getMachineLayerMax());

            for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
                allPolygons[physicalExtruder] = new PolygonList();
            }

            Point2D startNearHere = new Point2D(0, 0);
            for (int stl = 0; stl < stlList.size(); stl++) {
                PolygonList fills = stlList.computeInfill(stl);
                final PolygonList borders = stlList.computeOutlines(stl, fills);
                fills = fills.cullShorts(layerRules.getPrinter());
                final PolygonList support = stlList.computeSupport(stl);

                for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
                    tempBorderPolygons[physicalExtruder] = new PolygonList();
                    tempFillPolygons[physicalExtruder] = new PolygonList();
                }
                for (int pol = 0; pol < borders.size(); pol++) {
                    final Polygon p = borders.polygon(pol);
                    p.getAttributes();
                    tempBorderPolygons[printer.getExtruder(p.getAttributes().getMaterial()).getPhysicalExtruderNumber()].add(p);
                }
                for (int pol = 0; pol < fills.size(); pol++) {
                    final Polygon p = fills.polygon(pol);
                    p.getAttributes();
                    tempFillPolygons[printer.getExtruder(p.getAttributes().getMaterial()).getPhysicalExtruderNumber()].add(p);
                }
                for (int pol = 0; pol < support.size(); pol++) {
                    final Polygon p = support.polygon(pol);
                    p.getAttributes();
                    tempFillPolygons[printer.getExtruder(p.getAttributes().getMaterial()).getPhysicalExtruderNumber()].add(p);
                }

                for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
                    if (tempBorderPolygons[physicalExtruder].size() > 0) {
                        tempBorderPolygons[physicalExtruder].polygon(0).getAttributes();
                        double linkUp = printer.getExtruder(
                                tempBorderPolygons[physicalExtruder].polygon(0).getAttributes().getMaterial())
                                .getExtrusionSize();
                        linkUp = (4 * linkUp * linkUp);
                        tempBorderPolygons[physicalExtruder].radicalReOrder(linkUp, printer);
                        tempBorderPolygons[physicalExtruder] = tempBorderPolygons[physicalExtruder].nearEnds(startNearHere,
                                false, -1);
                        if (tempBorderPolygons[physicalExtruder].size() > 0) {
                            final Polygon last = tempBorderPolygons[physicalExtruder]
                                    .polygon(tempBorderPolygons[physicalExtruder].size() - 1);
                            startNearHere = last.point(last.size() - 1);
                        }
                        allPolygons[physicalExtruder].add(tempBorderPolygons[physicalExtruder]);
                    }
                    if (tempFillPolygons[physicalExtruder].size() > 0) {
                        tempFillPolygons[physicalExtruder].polygon(0).getAttributes();
                        double linkUp = printer.getExtruder(
                                tempFillPolygons[physicalExtruder].polygon(0).getAttributes().getMaterial()).getExtrusionSize();
                        linkUp = (4 * linkUp * linkUp);
                        tempFillPolygons[physicalExtruder].radicalReOrder(linkUp, printer);
                        tempFillPolygons[physicalExtruder] = tempFillPolygons[physicalExtruder].nearEnds(startNearHere, false,
                                -1);
                        if (tempFillPolygons[physicalExtruder].size() > 0) {
                            final Polygon last = tempFillPolygons[physicalExtruder].polygon(tempFillPolygons[physicalExtruder]
                                    .size() - 1);
                            startNearHere = last.point(last.size() - 1);
                        }
                        allPolygons[physicalExtruder].add(tempFillPolygons[physicalExtruder]);
                    }
                }
            }
            layerRules.setFirstAndLast(allPolygons);
            final LayerProducer lp = new LayerProducer(allPolygons, layerRules, simulationPlot);
            lp.plot();
            printer.finishedLayer(layerRules.getReversing());
            layerRules.step();
        }
        layerRules.layFoundationTopDown(simulationPlot);
        layerRules.reverseLayers();
    }

    public void dispose() {
        if (simulationPlot != null) {
            simulationPlot.dispose();
        }
    }
}
