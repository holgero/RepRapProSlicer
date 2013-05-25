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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.SceneGraphObject;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.configuration.MathRoutines;
import org.reprap.configuration.PrintSetting;
import org.reprap.gcode.Purge;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.BooleanGridList;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Circle;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.LineSegment;
import org.reprap.geometry.polygons.ParallelException;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.CSG3D;
import org.reprap.geometry.polyhedra.STLFileContents;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.io.stl.StlFileLoader;

class ProducerStlList {
    private static final Logger LOGGER = LogManager.getLogger(Producer.class);
    private static final double GRID_RESOLUTION = 0.01;
    private static final Slice EMPTY_SLICE = new Slice(new BooleanGridList());

    static BoundingBox calculateBoundingBox(final List<STLObject> stlList, final Purge purge,
            final CurrentConfiguration currentConfiguration) {
        setUpShield(purge, stlList, currentConfiguration);
        return getBoundingBox(stlList);
    }

    private final CurrentConfiguration currentConfiguration;
    private final List<STLObject> stlsToBuild;
    /**
     * A plan box round each item
     */
    private final List<Rectangle> rectangles = new ArrayList<Rectangle>();
    private final LayerRules layerRules;
    private final SliceCache cache;

    ProducerStlList(final List<STLObject> stlsToBuild, final LayerRules layerRules, final CurrentConfiguration currentConfiguration) {
        this.stlsToBuild = stlsToBuild;
        this.currentConfiguration = currentConfiguration;
        setRectangles(stlsToBuild, rectangles);
        this.layerRules = layerRules;
        cache = new SliceCache(layerRules.sliceCacheSize(), stlsToBuild.size());
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
    private static BoundingBox getBoundingBox(final List<STLObject> stls) {
        BoundingBox result = null;

        for (int i = 0; i < stls.size(); i++) {
            final STLObject stl = stls.get(i);
            final Transform3D trans = stl.getTransform();
            final BranchGroup bg = stl.getSTL();
            for (final Object object : Collections.list((Enumeration<?>) bg.getAllChildren())) {
                if (result == null) {
                    result = createBoundingBox(object, trans);
                } else {
                    final BoundingBox s = createBoundingBox(object, trans);
                    if (s != null) {
                        result.expand(s);
                    }
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
            final Transform3D trans = stl.getTransform();
            final BranchGroup bg = stl.getSTL();
            for (final Object object : Collections.list((Enumeration<?>) bg.getAllChildren())) {
                final BoundingBox s = createBoundingBox(object, trans);
                if (s != null) {
                    if (i < rectangles.size()) {
                        rectangles.set(i, Rectangle.union(rectangles.get(i), s.getXYbox()));
                    } else {
                        rectangles.add(new Rectangle(s.getXYbox()));
                    }
                }
            }
            if (rectangles.size() <= i) {
                LOGGER.error("object " + i + " is empty");
                rectangles.add(null);
            }
        }
    }

    /**
     * Unpack the Shape3D(s) from value and find their enclosing XYZ box
     */
    private static BoundingBox createBoundingBox(final Object value, final Transform3D trans) {
        BoundingBox result = null;

        if (value instanceof SceneGraphObject) {
            final SceneGraphObject sceneGraph = (SceneGraphObject) value;
            if (sceneGraph instanceof Group) {
                final Group group = (Group) sceneGraph;
                for (final Object object : Collections.list((Enumeration<?>) group.getAllChildren())) {
                    if (result == null) {
                        result = createBoundingBox(object, trans);
                    } else {
                        final BoundingBox s = createBoundingBox(object, trans);
                        if (s != null) {
                            result.expand(s);
                        }
                    }
                }
            } else if (sceneGraph instanceof Shape3D) {
                result = createShapeBoundingBox((Shape3D) sceneGraph, trans);
            }
        }

        return result;
    }

    /**
     * Run through a Shape3D and find its enclosing XYZ box
     */
    private static BoundingBox createShapeBoundingBox(final Shape3D shape, final Transform3D trans) {
        BoundingBox result = null;
        final GeometryArray geometry = (GeometryArray) shape.getGeometry();
        if (geometry != null) {
            final Point3d vertex = new Point3d();
            final Point3d transformed = new Point3d();
            for (int i = 0; i < geometry.getVertexCount(); i++) {
                geometry.getCoordinate(i, vertex);
                trans.transform(vertex, transformed);
                if (result == null) {
                    result = new BoundingBox(transformed);
                } else {
                    result.expand(transformed);
                }
            }
        }
        return result;
    }

    /**
     * Make sure the list starts with and edge longer than 1.5mm (or the longest
     * if not)
     */
    private static void startLong(final List<LineSegment> edges) {
        if (edges.size() <= 0) {
            return;
        }
        double d = -1;
        int swap = -1;
        LineSegment temp;
        for (int i = 0; i < edges.size(); i++) {
            final double d2 = Point2D.dSquared(edges.get(i).getA(), edges.get(i).getB());
            if (d2 > 2.25) {
                temp = edges.get(0);
                edges.set(0, edges.get(i));
                edges.set(i, temp);
                return;
            }
            if (d2 > d) {
                d = d2;
                swap = i;
            }
        }
        if (swap < 0) {
            LOGGER.error("startLong(): no edges found!");
            return;
        }
        temp = edges.get(0);
        edges.set(0, edges.get(swap));
        edges.set(swap, temp);
        if (Math.sqrt(d) < GRID_RESOLUTION) {
            LOGGER.debug("startLong(): edge length: " + Math.sqrt(d) + " is the longest.");
        }
    }

    /**
     * Stitch together the some of the edges to form a polygon.
     */
    private static Polygon getNextPolygon(final List<LineSegment> edges) {
        if (edges.size() <= 0) {
            return null;
        }
        startLong(edges);
        LineSegment next = edges.get(0);
        edges.remove(0);
        final Polygon result = new Polygon(next.getMaterial(), true);
        result.add(next.getA());
        result.add(next.getB());
        final Point2D start = next.getA();
        Point2D end = next.getB();

        boolean first = true;
        while (edges.size() > 0) {
            double d2 = Point2D.dSquared(start, end);
            if (first) {
                d2 = Math.max(d2, 1);
            }
            first = false;
            boolean aEnd = false;
            int index = -1;
            for (int i = 0; i < edges.size(); i++) {
                double dd = Point2D.dSquared(edges.get(i).getA(), end);
                if (dd < d2) {
                    d2 = dd;
                    aEnd = true;
                    index = i;
                }
                dd = Point2D.dSquared(edges.get(i).getB(), end);
                if (dd < d2) {
                    d2 = dd;
                    aEnd = false;
                    index = i;
                }
            }

            if (index >= 0) {
                next = edges.get(index);
                edges.remove(index);
                final int ipt = result.size() - 1;
                if (aEnd) {
                    result.set(ipt, Point2D.mul(Point2D.add(next.getA(), result.point(ipt)), 0.5));
                    result.add(next.getB());
                    end = next.getB();
                } else {
                    result.set(ipt, Point2D.mul(Point2D.add(next.getB(), result.point(ipt)), 0.5));
                    result.add(next.getA());
                    end = next.getA();
                }
            } else {
                return result;
            }
        }

        LOGGER.debug("getNextPolygon(): exhausted edge list!");

        return result;
    }

    /**
     * Get all the polygons represented by the edges.
     */
    private static PolygonList simpleCull(final List<LineSegment> edges) {
        final PolygonList result = new PolygonList();
        Polygon next = getNextPolygon(edges);
        while (next != null) {
            if (next.size() >= 3) {
                result.add(next);
            }
            next = getNextPolygon(edges);
        }

        return result;
    }

    /**
     * Compute the support hatching polygons for this set of patterns
     */
    PolygonList computeSupport(final int stl, final Slice slice) {
        // We start by computing the union of everything in this layer because
        // that is everywhere that support _isn't_ needed.
        final int layer = layerRules.getModelLayer();
        BooleanGrid unionOfThisLayer = slice.unionMaterials();

        final String material;
        if (!unionOfThisLayer.isEmpty()) {
            material = unionOfThisLayer.getMaterial();
            // Expand the union of this layer a bit, so that any support is a little clear of this layer's boundaries.
            unionOfThisLayer = unionOfThisLayer.createOffsetGrid(0.5);
        } else {
            // default to the stl to build
            material = stlsToBuild.get(stl).attributes(0).getMaterial();
        }
        // Get the layer above and union it with this layer.  That's what needs
        // support on the next layer down.
        final BooleanGridList previousSupport = cache.getSupport(layer + 1, stl);

        cache.setSupport(BooleanGridList.unions(previousSupport, slice.getBitmaps()), layer, stl);

        // Now we subtract the union of this layer from all the stuff requiring support in the layer above.
        BooleanGridList support = new BooleanGridList();
        if (previousSupport != null) {
            for (int i = 0; i < previousSupport.size(); i++) {
                final BooleanGrid above = previousSupport.get(i);
                support.add(BooleanGrid.difference(above, unionOfThisLayer, material));
            }
            support = support.unionDuplicates();
        }

        final int supportExtruderNo = currentConfiguration.getPrintSetting().getSupportExtruder();
        final MaterialSetting supportMaterial = currentConfiguration.getMaterials().get(supportExtruderNo);
        // Now force the attributes of the support pattern to be the support extruders
        // for all the materials in it.
        for (int i = 0; i < support.size(); i++) {
            final BooleanGrid grid = support.get(i);
            grid.setMaterial(supportMaterial.getName());
        }

        return hatch(support, layerRules, false, true, currentConfiguration);
    }

    private static void setUpShield(final Purge purge, final List<STLObject> stls,
            final CurrentConfiguration currentConfiguration) {
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        if (!printSetting.printShield()) {
            return;
        }
        final String shieldMaterial = currentConfiguration.getMaterials().get(0).getName();
        final STLFileContents stlFileContents = StlFileLoader.loadSTLFileContents(printSetting.getShieldStlFile());
        final STLObject shield = STLObject.createStlObjectFromFile(stlFileContents, shieldMaterial, currentConfiguration);

        final BoundingBox boxWithoutShield = getBoundingBox(stls);
        final double modelZMax = boxWithoutShield.getZint().high();
        final Vector3d shieldSize = shield.extent();
        shield.rScale(modelZMax / shieldSize.z, true);

        final double zOff = 0.5 * (modelZMax - shieldSize.z);
        final Point2D purgePoint = purge.getPurgeMiddle();
        double xOff = purgePoint.x();
        double yOff = purgePoint.y();
        if (!purge.isPurgeXOriented()) {
            shield.translate(new Vector3d(-0.5 * shieldSize.x, -0.5 * shieldSize.y, 0));
            final Transform3D t3d1 = shield.getTransform();
            final Transform3D t3d2 = new Transform3D();
            t3d2.rotZ(0.5 * Math.PI);
            t3d1.mul(t3d2);
            shield.setTransform(t3d1);
            shield.translate(new Vector3d(yOff, -xOff, zOff));
        } else {
            xOff -= 0.5 * shieldSize.x;
            yOff -= shieldSize.y;
            shield.translate(new Vector3d(xOff, yOff, zOff));
        }

        stls.add(0, shield);
    }

    private static final class EdgeAndCsgsCollector {
        final List<LineSegment> edges = new ArrayList<>();
        final List<CSG3D> csgs = new ArrayList<>();
        String material = null;
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

        final double currentZ = layerRules.getModelZ(layer) + layerRules.getZStep() * 0.5;
        final Map<String, EdgeAndCsgsCollector> collectorMap = collectEdgeLinesAndCsgs(stlIndex, currentZ);

        final BooleanGridList result = new BooleanGridList();
        // Turn them into lists of polygons, one for each material, then turn those into pixelmaps.
        for (final String material : collectorMap.keySet()) {
            final EdgeAndCsgsCollector collector = collectorMap.get(material);
            // Deal with CSG shapes (much simpler and faster).
            for (int i = 0; i < collector.csgs.size(); i++) {
                final CSG2D csgp = CSG3D.slice(collector.csgs.get(i), currentZ);
                result.add(new BooleanGrid(csgp, rectangles.get(stlIndex), collector.material, currentConfiguration
                        .getPrinterSetting().getMachineResolution() * 0.6));
            }

            // Deal with STL-generated edges
            if (collector.edges.size() > 0) {
                PolygonList pgl = simpleCull(collector.edges);

                if (pgl.size() > 0) {
                    pgl = pgl.simplify(GRID_RESOLUTION * 1.5);
                    pgl = arcCompensate(pgl);

                    final CSG2D csgp = pgl.toCSG();
                    result.add(new BooleanGrid(csgp, rectangles.get(stlIndex), collector.material, currentConfiguration
                            .getPrinterSetting().getMachineResolution() * 0.6));
                }
            }
        }

        return new Slice(result);
    }

    private Map<String, EdgeAndCsgsCollector> collectEdgeLinesAndCsgs(final int stlIndex, final double currentZ) {
        final Map<String, EdgeAndCsgsCollector> collectorMap = new HashMap<String, EdgeAndCsgsCollector>();
        for (final MaterialSetting material : currentConfiguration.getMaterials()) {
            collectorMap.put(material.getName(), new EdgeAndCsgsCollector());
        }

        // Generate all the edges for STLObject i at this z
        final STLObject stlObject = stlsToBuild.get(stlIndex);
        final Transform3D trans = stlObject.getTransform();
        final Matrix4d m4 = new Matrix4d();
        trans.get(m4);

        for (int i = 0; i < stlObject.size(); i++) {
            final BranchGroup group = stlObject.getSTL(i);
            final String material = ((Attributes) (group.getUserData())).getMaterial();
            final EdgeAndCsgsCollector collector = collectorMap.get(material);
            collector.material = material;
            final CSG3D csg = stlObject.getCSG(i);
            if (csg != null) {
                collector.csgs.add(csg.transform(m4));
            } else {
                recursiveSetEdges(group, trans, currentZ, material, collector.edges);
            }
        }
        return collectorMap;
    }

    /**
     * Add the edge where the plane z cuts the triangle (p, q, r) (if it does).
     * Also update the triangulation of the object below the current slice used
     * for the simulation window.
     */
    private static void addEdge(final Point3d p, final Point3d q, final Point3d r, final double z, final String material,
            final List<LineSegment> edges) {
        Point3d odd;
        Point3d even1;
        Point3d even2;
        int pat = 0;

        if (p.z < z) {
            pat = pat | 1;
        }
        if (q.z < z) {
            pat = pat | 2;
        }
        if (r.z < z) {
            pat = pat | 4;
        }

        switch (pat) {
        // All above
        case 0:
            return;
            // All below
        case 7:
            return;
            // q, r below, p above	
        case 6:
            //twoBelow = true;
            // p below, q, r above
        case 1:
            odd = p;
            even1 = q;
            even2 = r;
            break;
        // p, r below, q above	
        case 5:
            //twoBelow = true;
            // q below, p, r above	
        case 2:
            odd = q;
            even1 = r;
            even2 = p;
            break;
        // p, q below, r above	
        case 3:
            //twoBelow = true;
            // r below, p, q above	
        case 4:
            odd = r;
            even1 = p;
            even2 = q;
            break;
        default:
            throw new RuntimeException("addEdge(): the | function doesn't seem to work...");
        }

        // Work out the intersection line segment (e1 -> e2) between the z plane and the triangle
        even1.sub(odd);
        even2.sub(odd);
        double t = (z - odd.z) / even1.z;
        Point2D e1 = new Point2D(odd.x + t * even1.x, odd.y + t * even1.y);
        e1 = new Point2D(e1.x(), e1.y());
        t = (z - odd.z) / even2.z;
        Point2D e2 = new Point2D(odd.x + t * even2.x, odd.y + t * even2.y);
        e2 = new Point2D(e2.x(), e2.y());

        // Too short?
        edges.add(new LineSegment(e1, e2, material));
    }

    /**
     * Run through a Shape3D and set edges from it at plane z Apply the
     * transform first
     */
    private static void addAllEdges(final Shape3D shape, final Transform3D trans, final double z, final String material,
            final List<LineSegment> edges) {
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d p1 = new Point3d();
        final Point3d p2 = new Point3d();
        final Point3d p3 = new Point3d();
        final Point3d q1 = new Point3d();
        final Point3d q2 = new Point3d();
        final Point3d q3 = new Point3d();

        if (g.getVertexCount() % 3 != 0) {
            LOGGER.error("addAllEdges(): shape3D with vertices not a multiple of 3!");
        }
        for (int i = 0; i < g.getVertexCount(); i += 3) {
            g.getCoordinate(i, p1);
            g.getCoordinate(i + 1, p2);
            g.getCoordinate(i + 2, p3);
            trans.transform(p1, q1);
            trans.transform(p2, q2);
            trans.transform(p3, q3);
            addEdge(q1, q2, q3, z, material, edges);
        }
    }

    /**
     * Unpack the Shape3D(s) from value and set edges from them
     */
    private void recursiveSetEdges(final Object value, final Transform3D trans, final double z, final String material,
            final List<LineSegment> edges) {
        if (value instanceof SceneGraphObject) {
            final SceneGraphObject sg = (SceneGraphObject) value;
            if (sg instanceof Group) {
                final Group g = (Group) sg;
                final java.util.Enumeration<?> enumKids = g.getAllChildren();
                while (enumKids.hasMoreElements()) {
                    recursiveSetEdges(enumKids.nextElement(), trans, z, material, edges);
                }
            } else if (sg instanceof Shape3D) {
                addAllEdges((Shape3D) sg, trans, z, material, edges);
            }
        }
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
     * 
     * This ignores speeds
     * 
     * @param es
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

    /**
     * Work out all the open polygons forming a set of infill hatches. If
     * surface is true, these polygons are on the outside (top or bottom). If
     * it's false they are in the interior.The hatch is provided by
     * layerConditions.
     */
    static PolygonList hatch(final BooleanGridList list, final LayerRules layerConditions, final boolean surface,
            final boolean support, final CurrentConfiguration currentConfiguration) {
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        final PolygonList result = new PolygonList();
        for (int i = 0; i < list.size(); i++) {
            final BooleanGrid grid = list.get(i);
            final String material = grid.getMaterial();
            final double infillWidth;
            if (support) {
                infillWidth = printSetting.getSupportSpacing();
            } else {
                final double extrusionSize = currentConfiguration.getExtruderSetting(material).getExtrusionSize();
                if (surface) {
                    infillWidth = extrusionSize;
                } else {
                    infillWidth = extrusionSize / printSetting.getFillDensity();
                }
            }
            final HalfPlane hatchLine = layerConditions.getHatchDirection(support, infillWidth);
            result.add(grid.hatch(hatchLine, infillWidth, currentConfiguration.getPrintSetting().isPathOptimize()));
        }
        return result;
    }
}
