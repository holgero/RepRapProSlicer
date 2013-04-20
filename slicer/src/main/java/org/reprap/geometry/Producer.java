package org.reprap.geometry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.Main;
import org.reprap.configuration.Preferences;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.BoundingBox;
import org.reprap.gui.SlicerFrame;

public class Producer {
    private static final Logger LOGGER = LogManager.getLogger(Producer.class);
    private LayerRules layerRules = null;
    private SimulationPlotter simulationPlot = null;

    /**
     * The list of objects to be built
     */
    private final ProducerStlList stlList;
    private final SlicerFrame slicerFrame;
    private final Preferences preferences = Preferences.getInstance();

    public Producer(final GCodePrinter pr, final AllSTLsToBuild allStls, final SlicerFrame slicerFrame) throws Exception {
        this.slicerFrame = slicerFrame;

        final Point2D purge = new Point2D(preferences.loadDouble("DumpX(mm)"), preferences.loadDouble("DumpY(mm)"));
        final BoundingBox buildVolume = ProducerStlList.calculateBoundingBox(allStls, purge);
        layerRules = new LayerRules(pr, buildVolume, purge);
        stlList = new ProducerStlList(allStls, purge, layerRules);
        pr.setLayerRules(layerRules);

        if (slicerFrame.displayPaths()) {
            simulationPlot = new SimulationPlotter("RepRap building simulation");
        } else {
            simulationPlot = null;
        }
    }

    public int getLayers() {
        return layerRules.getMachineLayerMax();
    }

    public int getLayer() {
        return layerRules.getMachineLayer();
    }

    public void produce() throws Exception {
        produceAdditiveTopDown();
    }

    private void fillFoundationRectangle(final GCodePrinter reprap, final Rectangle gp) throws Exception {
        final PolygonList shield = new PolygonList();
        final GCodeExtruder e = reprap.getExtruder();
        final Attributes fa = new Attributes(e.getMaterial(), null, e.getAppearance());
        final CSG2D rect = CSG2D.RrCSGFromBox(gp);
        final BooleanGrid bg = new BooleanGrid(rect, gp.scale(1.1), fa);
        final PolygonList h[] = { shield,
                bg.hatch(layerRules.getHatchDirection(e, false), layerRules.getHatchWidth(e), bg.attribute()) };
        final LayerProducer lp = new LayerProducer(h, layerRules, simulationPlot);
        lp.plot();
        reprap.getExtruder().stopExtruding();
    }

    private void layFoundationTopDown(final Rectangle gp) throws Exception {
        if (layerRules.getFoundationLayers() <= 0) {
            return;
        }

        layerRules.setLayingSupport(true);
        layerRules.getPrinter().setSeparating(false);
        final GCodePrinter reprap = layerRules.getPrinter();
        while (layerRules.getMachineLayer() >= 0) {
            LOGGER.debug("Commencing foundation layer at " + layerRules.getMachineZ());
            reprap.startingLayer(layerRules);
            fillFoundationRectangle(reprap, gp);
            reprap.finishedLayer(layerRules);
            layerRules.stepMachine();
        }
    }

    private void produceAdditiveTopDown() throws Exception {
        final GCodePrinter reprap = layerRules.getPrinter();
        layerRules.setLayingSupport(false);
        int lastExtruder = -1;
        int totalPhysicalExtruders = 0;
        for (int extruder = 0; extruder < reprap.getExtruders().length; extruder++) {
            final int thisExtruder = reprap.getExtruders()[extruder].getPhysicalExtruderNumber();
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
                reprap.setSeparating(true);
            } else {
                reprap.setSeparating(false);
            }

            LOGGER.debug("Commencing model layer " + layerRules.getModelLayer() + " at " + layerRules.getMachineZ());
            reprap.startingLayer(layerRules);
            slicerFrame.updateProgress();

            for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
                allPolygons[physicalExtruder] = new PolygonList();
            }

            Point2D startNearHere = new Point2D(0, 0);
            for (int stl = 0; stl < stlList.size(); stl++) {
                PolygonList fills = stlList.computeInfill(stl);
                final PolygonList borders = stlList.computeOutlines(stl, fills);
                fills = fills.cullShorts();
                final PolygonList support = stlList.computeSupport(stl);

                for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
                    tempBorderPolygons[physicalExtruder] = new PolygonList();
                    tempFillPolygons[physicalExtruder] = new PolygonList();
                }
                for (int pol = 0; pol < borders.size(); pol++) {
                    final Polygon p = borders.polygon(pol);
                    p.getAttributes();
                    tempBorderPolygons[Main.getExtruder(p.getAttributes().getMaterial()).getPhysicalExtruderNumber()].add(p);
                }
                for (int pol = 0; pol < fills.size(); pol++) {
                    final Polygon p = fills.polygon(pol);
                    p.getAttributes();
                    tempFillPolygons[Main.getExtruder(p.getAttributes().getMaterial()).getPhysicalExtruderNumber()].add(p);
                }
                for (int pol = 0; pol < support.size(); pol++) {
                    final Polygon p = support.polygon(pol);
                    p.getAttributes();
                    tempFillPolygons[Main.getExtruder(p.getAttributes().getMaterial()).getPhysicalExtruderNumber()].add(p);
                }

                for (int physicalExtruder = 0; physicalExtruder < allPolygons.length; physicalExtruder++) {
                    if (tempBorderPolygons[physicalExtruder].size() > 0) {
                        tempBorderPolygons[physicalExtruder].polygon(0).getAttributes();
                        double linkUp = Main.getExtruder(
                                tempBorderPolygons[physicalExtruder].polygon(0).getAttributes().getMaterial())
                                .getExtrusionSize();
                        linkUp = (4 * linkUp * linkUp);
                        tempBorderPolygons[physicalExtruder].radicalReOrder(linkUp);
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
                        double linkUp = Main.getExtruder(
                                tempFillPolygons[physicalExtruder].polygon(0).getAttributes().getMaterial()).getExtrusionSize();
                        linkUp = (4 * linkUp * linkUp);
                        tempFillPolygons[physicalExtruder].radicalReOrder(linkUp);
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
            reprap.finishedLayer(layerRules);
            layerRules.step();
        }
        layFoundationTopDown(layerRules.getBox());
        layerRules.reverseLayers();
    }
}
