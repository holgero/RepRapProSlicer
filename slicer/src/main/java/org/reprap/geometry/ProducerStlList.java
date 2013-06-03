/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *   most coding has been extracted from 
 *   org.reprap.geometry.polyhedra.AllSTLsToBuild
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.reprap.geometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Transform3D;
import javax.vecmath.Matrix4d;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.configuration.PrintSetting;
import org.reprap.configuration.store.MathRoutines;
import org.reprap.geometry.grids.BooleanGrid;
import org.reprap.geometry.grids.BooleanGridList;
import org.reprap.geometry.grids.Hatcher;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Circle;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.ParallelException;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.BoundingBox;
import org.reprap.geometry.polyhedra.STLObject;

class ProducerStlList {
    static final Logger LOGGER = LogManager.getLogger(Producer.class);
    private static final double GRID_RESOLUTION = 0.01;
    private static final Slice EMPTY_SLICE = new Slice(new BooleanGridList());

    private final CurrentConfiguration currentConfiguration;
    private final List<STLObject> stlsToBuild;
    /**
     * A plan box round each item
     */
    private final List<Rectangle> rectangles = new ArrayList<Rectangle>();
    private final LayerRules layerRules;

    ProducerStlList(final List<STLObject> stlsToBuild, final LayerRules layerRules,
            final CurrentConfiguration currentConfiguration) {
        this.stlsToBuild = stlsToBuild;
        this.currentConfiguration = currentConfiguration;
        setRectangles(stlsToBuild, rectangles);
        this.layerRules = layerRules;
    }

    /**
     * Return the number of objects.
     */
    int size() {
        return stlsToBuild.size();
    }

    /**
     * calculate the bounding box of all STLs in a list.
     */
    static BoundingBox getBoundingBox(final List<STLObject> stls) {
        BoundingBox result = null;

        for (int i = 0; i < stls.size(); i++) {
            final STLObject stl = stls.get(i);
            final BoundingBox nextBox = stl.getBoundingBox();
            if (result == null) {
                result = nextBox;
            } else {
                if (nextBox != null) {
                    result.expand(nextBox);
                }
            }
        }
        return result;
    }

    /**
     * Scan everything loaded and set up the rectangles
     */
    private static void setRectangles(final List<STLObject> stls, final List<Rectangle> rectangles) {
        for (int i = 0; i < stls.size(); i++) {
            final STLObject stl = stls.get(i);
            final BoundingBox box = stl.getBoundingBox();
            if (box == null) {
                LOGGER.error("object " + i + " is empty");
                rectangles.add(null);
            } else {
                rectangles.add(new Rectangle(box.getXYbox()));
            }
        }
    }

    /**
     * Generate a set of pixel-map representations, one for each material, for
     * STLObject stl at height z.
     */
    Slice slice(final int stlIndex, final int layer) {
        if (layer < 0) {
            return EMPTY_SLICE;
        }

        if (rectangles.get(stlIndex) == null) {
            return EMPTY_SLICE;
        }

        final double currentZ = layerRules.getModelZ(layer);
        final Map<String, EdgeCollector> collectorMap = collectEdgeLinesAndCsgs(stlIndex, currentZ);

        final BooleanGridList result = new BooleanGridList();
        // Turn them into lists of polygons, one for each material, then turn those into pixelmaps.
        for (final String material : collectorMap.keySet()) {
            final EdgeCollector collector = collectorMap.get(material);
            PolygonList pgl = collector.simpleCull(material);

            if (pgl.size() > 0) {
                pgl = pgl.simplify(GRID_RESOLUTION * 1.5);
                pgl = arcCompensate(pgl);

                final CSG2D csgp = pgl.toCSG();
                result.add(new BooleanGrid(currentConfiguration.getPrinterSetting().getMachineResolution() * 0.6, material,
                        rectangles.get(stlIndex), csgp));
            }
        }

        return new Slice(result);
    }

    private Map<String, EdgeCollector> collectEdgeLinesAndCsgs(final int stlIndex, final double currentZ) {
        final Map<String, EdgeCollector> collectorMap = new HashMap<String, EdgeCollector>();
        for (final MaterialSetting material : currentConfiguration.getMaterials()) {
            collectorMap.put(material.getName(), new EdgeCollector());
        }

        // Generate all the edges for STLObject i at this z
        final STLObject stlObject = stlsToBuild.get(stlIndex);
        final Transform3D trans = stlObject.getTransform();
        final Matrix4d m4 = new Matrix4d();
        trans.get(m4);

        for (int i = 0; i < stlObject.size(); i++) {
            final BranchGroup group = stlObject.getSTL(i);
            final String material = ((Attributes) (group.getUserData())).getMaterial();
            final EdgeCollector collector = collectorMap.get(material);
            collector.recursiveSetEdges(group, trans, currentZ);
        }
        return collectorMap;
    }

    /**
     * Offset (some of) the points in the polygons to allow for the fact that
     * extruded circles otherwise don't come out right. See
     * http://reprap.org/bin/view/Main/ArcCompensation.
     */
    private PolygonList arcCompensate(final PolygonList list) {
        final PolygonList result = new PolygonList();

        for (int i = 0; i < list.size(); i++) {
            final Polygon p = list.polygon(i);
            result.add(arcCompensate(p));
        }

        return result;
    }

    /**
     * Offset (some of) the points in the polygon to allow for the fact that
     * extruded circles otherwise don't come out right. See
     * http://reprap.org/bin/view/Main/ArcCompensation. If the extruder for the
     * polygon's arc compensation factor is 0, return the polygon unmodified.
     */
    private Polygon arcCompensate(final Polygon polygon) {
        final String material = polygon.getMaterial();

        // Multiply the geometrically correct result by factor
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        final double factor = printSetting.getArcCompensation();
        if (factor < MathRoutines.TINY_VALUE) {
            return polygon;
        }

        // The points making the arc must be closer than this together
        final double shortSides = printSetting.getArcShortSides();
        final double thickness = currentConfiguration.getExtruderSetting(material).getExtrusionSize();
        final Polygon result = new Polygon(material, polygon.isClosed());
        Point2D previous = polygon.point(polygon.size() - 1);
        Point2D current = polygon.point(0);

        double d1 = Point2D.dSquared(current, previous);
        final double short2 = shortSides * shortSides;
        final double t2 = thickness * thickness;

        for (int i = 0; i < polygon.size(); i++) {
            final Point2D next;
            if (i == polygon.size() - 1) {
                next = polygon.point(0);
            } else {
                next = polygon.point(i + 1);
            }

            final double d2 = Point2D.dSquared(next, current);
            if (d1 < short2 && d2 < short2) {
                try {
                    final Circle c = new Circle(previous, current, next);
                    final double offset = factor * (Math.sqrt(t2 + 4 * c.radiusSquared()) * 0.5 - Math.sqrt(c.radiusSquared()));
                    Point2D offsetPoint = Point2D.sub(current, c.centre());
                    offsetPoint = Point2D.add(current, Point2D.mul(offsetPoint.norm(), offset));
                    result.add(offsetPoint);
                } catch (final ParallelException ex) {
                    result.add(current);
                }
            } else {
                result.add(current);
            }

            d1 = d2;
            previous = current;
            current = next;
        }

        return result;
    }

    static double getMaxMultimaterialZ(final List<STLObject> stlObjects) {
        final Map<String, Double> materialMaxZMap = new HashMap<String, Double>();

        for (final STLObject stlObject : stlObjects) {
            stlObject.collectMaxZPerMaterial(materialMaxZMap);
        }
        if (materialMaxZMap.size() < 2) {
            return 0.0;
        }
        return secondHeighestValue(materialMaxZMap.values());
    }

    private static double secondHeighestValue(final Collection<Double> values) {
        final List<Double> heights = new ArrayList<>(values);
        Collections.sort(heights);
        return heights.get(heights.size() - 2);
    }

    /**
     * Work out all the open polygons forming a set of infill hatches. If
     * surface is true, these polygons are on the outside (top or bottom). If
     * it's false they are in the interior.The hatch is provided by
     * layerConditions.
     */
    static PolygonList hatch(final BooleanGridList list, final LayerRules layerConditions, final boolean surface,
            final CurrentConfiguration currentConfiguration) {
        final PolygonList result = new PolygonList();
        for (int i = 0; i < list.size(); i++) {
            final PolygonList polygons = hatch(list.get(i), layerConditions, surface, currentConfiguration);
            result.add(polygons);
        }
        return result;
    }

    private static PolygonList hatch(final BooleanGrid grid, final LayerRules layerConditions, final boolean surface,
            final CurrentConfiguration currentConfiguration) {
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        final String material = grid.getMaterial();
        final double infillWidth;
        final double extrusionSize = currentConfiguration.getExtruderSetting(material).getExtrusionSize();
        if (surface) {
            infillWidth = extrusionSize;
        } else {
            infillWidth = extrusionSize / printSetting.getFillDensity();
        }
        final HalfPlane hatchLine = layerConditions.getFillHatchLine(infillWidth);
        return new Hatcher(grid).hatch(hatchLine, infillWidth, printSetting.isPathOptimize());
    }
}
