package org.reprap.geometry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.BooleanGridList;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polyhedra.Attributes;

/**
 * Class to hold infill patterns
 * 
 * @author ensab
 */
public final class InFillPatterns {
    private static final Logger LOGGER = LogManager.getLogger(InFillPatterns.class);
    private BooleanGridList bridges;
    private BooleanGridList insides;
    private BooleanGridList surfaces;
    private final PolygonList hatchedPolygons;

    public InFillPatterns() {
        bridges = new BooleanGridList();
        insides = new BooleanGridList();
        surfaces = new BooleanGridList();
        hatchedPolygons = new PolygonList();
    }

    PolygonList computeHatchedPolygons(final int stl, final LayerRules layerRules, final ProducerStlList slicer) {
        // Where are we and what does the current slice look like?
        final int layer = layerRules.getModelLayer();
        BooleanGridList slice = slicer.slice(stl, layer);

        int surfaceLayers = 1;
        for (int i = 0; i < slice.size(); i++) {
            final GCodeExtruder extruder = layerRules.getPrinter().getExtruder(slice.get(i).attribute().getMaterial());
            if (extruder.getSurfaceLayers() > surfaceLayers) {
                surfaceLayers = extruder.getSurfaceLayers();
            }
        }

        // Get the bottom out of the way - no fancy calculations needed.
        if (layer <= surfaceLayers) {
            slice = ProducerStlList.offset(slice, layerRules, -1);
            slice = slicer.neededThisLayer(slice, false, false);
            return ProducerStlList.hatch(slice, layerRules, true, false);
        }

        // If we are solid but the slices above or below us weren't, we need some fine infill as
        // we are (at least partly) surface.
        // The intersection of the slices above does not need surface ..
        // How many do we need to consider?
        BooleanGridList above = slicer.slice(stl, layer + 1);
        for (int i = 2; i <= surfaceLayers; i++) {
            above = BooleanGridList.intersections(slicer.slice(stl, layer + i), above);
        }

        // ...nor does the intersection of those below.
        BooleanGridList below = slicer.slice(stl, layer - 1);
        for (int i = 2; i <= surfaceLayers; i++) {
            below = BooleanGridList.intersections(slicer.slice(stl, layer - i), below);
        }

        // The bit of the slice with nothing above it needs fine ..
        final BooleanGridList nothingabove = BooleanGridList.differences(slice, above, false);

        // ...as does the bit with nothing below.
        final BooleanGridList nothingbelow = BooleanGridList.differences(slice, below, false);

        // Find the region that is not surface.
        insides = BooleanGridList.differences(slice, nothingbelow, false);
        insides = BooleanGridList.differences(insides, nothingabove, false);

        // Parts with nothing under them that have no support material
        // need to have bridges constructed to do the best for in-air 
        bridges = nothingbelow.cullNoSupport(layerRules.getPrinter());

        // The remainder with nothing under them will be supported by support material
        // and so needs no special treatment.
        // All the parts of this slice that need surface infill

        surfaces = BooleanGridList.unions(nothingbelow, nothingabove);

        // Make the bridges fatter, then crop them to the slice.
        // This will make them interpenetrate at their ends/sides to give
        // bridge landing areas.
        bridges = ProducerStlList.offset(bridges, layerRules, 2);
        bridges = BooleanGridList.intersections(bridges, slice);

        // Find the landing areas as a separate set of shapes that go with the bridges.
        final BooleanGridList lands = BooleanGridList.intersections(bridges, BooleanGridList.unions(insides, surfaces));

        // Shapes will be outlined, and so need to be shrunk to allow for that.  But they
        // must not also shrink from each other internally.  So initially expand them so they overlap
        bridges = ProducerStlList.offset(bridges, layerRules, 1);
        insides = ProducerStlList.offset(insides, layerRules, 1);
        surfaces = ProducerStlList.offset(surfaces, layerRules, 1);

        // Now intersect them with the slice so the outer edges are back where they should be.
        bridges = BooleanGridList.intersections(bridges, slice);
        insides = BooleanGridList.intersections(insides, slice);
        surfaces = BooleanGridList.intersections(surfaces, slice);

        // Now shrink them so the edges are in a bit to allow the outlines to
        // be put round the outside.  The inner joins should now shrink back to be
        // adjacent to each other as they should be.
        bridges = ProducerStlList.offset(bridges, layerRules, -1);
        insides = ProducerStlList.offset(insides, layerRules, -1);
        surfaces = ProducerStlList.offset(surfaces, layerRules, -1);

        // Generate the infill patterns.  We do the bridges first, as each bridge subtracts its
        // lands from the other two sets of shapes.  We want that, so they don't get infilled twice.
        bridgeHatch(lands, layerRules);
        insides = slicer.neededThisLayer(insides, true, false);
        hatchedPolygons.add(ProducerStlList.hatch(insides, layerRules, false, false));
        surfaces = slicer.neededThisLayer(surfaces, false, false);
        hatchedPolygons.add(ProducerStlList.hatch(surfaces, layerRules, true, false));

        return hatchedPolygons;
    }

    /**
     * This finds an individual land in landPattern
     */
    private BooleanGrid findLand(final BooleanGrid landPattern) {
        final Point2D seed = landPattern.findSeed();
        if (seed == null) {
            return null;
        }

        return landPattern.floodCopy(seed);
    }

    /**
     * This finds the bridge that cover cen. It assumes that there is only one
     * material at one point in space...
     */
    private int findBridge(final BooleanGridList unSupported, final Point2D cen) {
        for (int i = 0; i < unSupported.size(); i++) {
            final BooleanGrid bridge = unSupported.get(i);
            if (bridge.get(cen)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Compute the bridge infill for unsupported polygons for a slice. This is
     * very heuristic...
     */
    private void bridgeHatch(final BooleanGridList lands, final LayerRules layerConditions) {
        for (int i = 0; i < lands.size(); i++) {
            BooleanGrid landPattern = lands.get(i);
            BooleanGrid land1;

            while ((land1 = findLand(landPattern)) != null) {
                // Find the middle of the land
                final Point2D cen1 = land1.findCentroid();

                // Wipe this land from the land pattern
                landPattern = BooleanGrid.difference(landPattern, land1);

                if (cen1 == null) {
                    LOGGER.error("First land found with no centroid!");
                    continue;
                }

                // Find the bridge that goes with the land
                final int bridgesIndex = findBridge(bridges, cen1);
                if (bridgesIndex < 0) {
                    LOGGER.debug("Land found with no corresponding bridge.");
                    continue;
                }
                final BooleanGrid bridgeStart = bridges.get(bridgesIndex);

                // The bridge must cover the land too
                final BooleanGrid bridge = bridgeStart.floodCopy(cen1);
                if (bridge == null) {
                    continue;
                }

                // Find the other land (the first has been wiped)
                final BooleanGrid land2 = BooleanGrid.intersection(bridge, landPattern);

                // Find the middle of this land
                final Point2D cen2 = land2.findCentroid();
                final Attributes attribute = bridge.attribute();
                final GCodeExtruder extruder = layerConditions.getPrinter().getExtruder(attribute.getMaterial());
                if (cen2 == null) {
                    LOGGER.debug("Second land found with no centroid.");
                    // No second land implies a ring of support - just infill it.
                    hatchedPolygons.add(bridge.hatch(layerConditions.getHatchDirection(extruder, false),
                            extruder.getExtrusionInfillWidth(), attribute));

                    // Remove this bridge (in fact, just its lands) from the other infill patterns.
                    final BooleanGridList b = new BooleanGridList();
                    b.add(bridge);
                    insides = BooleanGridList.differences(insides, b, false);
                    surfaces = BooleanGridList.differences(surfaces, b, false);
                } else {
                    // Wipe this land from the land pattern
                    landPattern = BooleanGrid.difference(landPattern, land2);

                    // (Roughly) what direction does the bridge go in?
                    final Point2D centroidDirection = Point2D.sub(cen2, cen1).norm();
                    Point2D bridgeDirection = centroidDirection;

                    // Fine the edge of the bridge that is nearest parallel to that, and use that as the fill direction
                    double spMax = Double.NEGATIVE_INFINITY;
                    double sp;
                    final PolygonList bridgeOutline = bridge.allPerimiters(attribute);
                    for (int pol = 0; pol < bridgeOutline.size(); pol++) {
                        final Polygon polygon = bridgeOutline.polygon(pol);

                        for (int vertex1 = 0; vertex1 < polygon.size(); vertex1++) {
                            int vertex2 = vertex1 + 1;
                            if (vertex2 >= polygon.size()) {
                                vertex2 = 0;
                            }
                            final Point2D edge = Point2D.sub(polygon.point(vertex2), polygon.point(vertex1));

                            if ((sp = Math.abs(Point2D.mul(edge, centroidDirection))) > spMax) {
                                spMax = sp;
                                bridgeDirection = edge;
                            }

                        }
                    }

                    // Build the bridge
                    hatchedPolygons.add(bridge.hatch(new HalfPlane(new Point2D(0, 0), bridgeDirection),
                            extruder.getExtrusionInfillWidth(), attribute));

                    // Remove this bridge (in fact, just its lands) from the other infill patterns. 
                    final BooleanGridList b = new BooleanGridList();
                    b.add(bridge);
                    insides = BooleanGridList.differences(insides, b, false);
                    surfaces = BooleanGridList.differences(surfaces, b, false);
                }
                // remove the bridge from the bridge patterns.
                final BooleanGridList b = new BooleanGridList();
                b.add(bridge);
                bridges = BooleanGridList.differences(bridges, b, false);
            }
        }
    }
}