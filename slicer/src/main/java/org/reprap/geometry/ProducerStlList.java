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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

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
import org.reprap.configuration.Constants;
import org.reprap.configuration.Preferences;
import org.reprap.configuration.PrintSettings;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.gcode.GCodePrinter;
import org.reprap.gcode.Purge;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.BooleanGridList;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Circle;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.Line;
import org.reprap.geometry.polygons.ParallelException;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonIndexedPoint;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.BoundingBox;
import org.reprap.geometry.polyhedra.CSG3D;
import org.reprap.geometry.polyhedra.LineSegment;
import org.reprap.geometry.polyhedra.STLObject;

class ProducerStlList {
    private static final Logger LOGGER = LogManager.getLogger(Producer.class);

    static BoundingBox calculateBoundingBox(final AllSTLsToBuild allStls, final Purge purge) {
        final List<STLObject> stlList = new ArrayList<>();
        copyAllStls(allStls, stlList);
        setUpShield(purge, stlList, Preferences.getInstance());
        return getBoundingBox(stlList);
    }

    private final Preferences preferences = Preferences.getInstance();
    private final List<STLObject> stlsToBuild = new ArrayList<STLObject>();
    /**
     * A plan box round each item
     */
    private final List<Rectangle> rectangles = new ArrayList<Rectangle>();
    private final LayerRules layerRules;
    private final SliceCache cache;

    ProducerStlList(final AllSTLsToBuild allStls, final Purge purge, final LayerRules layerRules) {
        copyAllStls(allStls, stlsToBuild);
        setUpShield(purge, stlsToBuild, preferences);
        setRectangles(stlsToBuild, rectangles);
        this.layerRules = layerRules;
        cache = new SliceCache(layerRules, stlsToBuild);
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
    private void startLong(final ArrayList<LineSegment> edges) {
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
        if (Math.sqrt(d) < preferences.gridResultion()) {
            LOGGER.debug("startLong(): edge length: " + Math.sqrt(d) + " is the longest.");
        }
    }

    /**
     * Stitch together the some of the edges to form a polygon.
     */
    private Polygon getNextPolygon(final ArrayList<LineSegment> edges) {
        if (edges.size() <= 0) {
            return null;
        }
        startLong(edges);
        LineSegment next = edges.get(0);
        edges.remove(0);
        final Polygon result = new Polygon(next.getAttribute(), true);
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
    private PolygonList simpleCull(final ArrayList<LineSegment> edges) {
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
    PolygonList computeSupport(final int stl) {
        // We start by computing the union of everything in this layer because
        // that is everywhere that support _isn't_ needed.
        final int layer = layerRules.getModelLayer();
        BooleanGrid unionOfThisLayer = union(slice(stl, layer));

        final Attributes attribute;
        if (!unionOfThisLayer.isEmpty()) {
            attribute = unionOfThisLayer.attribute();
            // Expand the union of this layer a bit, so that any support is a little clear of 
            // this layer's boundaries.
            final BooleanGridList allThis = offsetOutline(unionOfThisLayer, layerRules, 2);
            // the first grid in the resulting grid list is now offset by its extruders extrusionSize.
            unionOfThisLayer = allThis.get(0);
        } else {
            // default to the stl to build
            attribute = stlsToBuild.get(stl).attributes(0);
        }
        // Get the layer above and union it with this layer.  That's what needs
        // support on the next layer down.
        final BooleanGridList previousSupport = cache.getSupport(layer + 1, stl);

        cache.setSupport(BooleanGridList.unions(previousSupport, slice(stl, layer)), layer, stl);

        final GCodeExtruder supportExtruder = layerRules.getPrinter().getExtruders()[preferences.getPrintSettings()
                .getSupportExtruder()];

        // Now we subtract the union of this layer from all the stuff requiring support in the layer above.
        BooleanGridList support = new BooleanGridList();
        if (previousSupport != null) {
            for (int i = 0; i < previousSupport.size(); i++) {
                final BooleanGrid above = previousSupport.get(i);
                support.add(BooleanGrid.difference(above, unionOfThisLayer, attribute));
            }
            support = support.unionDuplicates();
        }

        // Now force the attributes of the support pattern to be the support extruders
        // for all the materials in it.  If the material isn't active in this layer, remove it from the list
        for (int i = 0; i < support.size(); i++) {
            final BooleanGrid grid = support.get(i);
            grid.forceAttribute(new Attributes(supportExtruder.getMaterial(), null, supportExtruder.getAppearance()));
        }

        return hatch(support, layerRules, false, true);
    }

    private BooleanGrid union(final BooleanGridList slice) {
        if (slice.size() == 0) {
            return BooleanGrid.nullBooleanGrid();
        }
        BooleanGrid unionOfThisLayer = slice.get(0);
        // We give the union the attribute of the first thing found, though
        // clearly it will - in general - represent many different substances.
        // But it's only going to be subtracted from other shapes, so what it's made
        // from doesn't matter.
        final Attributes attributes = unionOfThisLayer.attribute();
        for (int i = 1; i < slice.size(); i++) {
            unionOfThisLayer = BooleanGrid.union(unionOfThisLayer, slice.get(i), attributes);
        }
        return unionOfThisLayer;
    }

    /**
     * Select from a slice (allLayer) just those parts of it that will be
     * plotted this layer
     */
    BooleanGridList neededThisLayer(final BooleanGridList allLayer) {
        final BooleanGridList neededSlice = new BooleanGridList();
        for (int i = 0; i < allLayer.size(); i++) {
            final Attributes attribute = allLayer.get(i).attribute();
            final GCodeExtruder e = layerRules.getPrinter().getExtruder(attribute.getMaterial());
            if (e != null) {
                neededSlice.add(allLayer.get(i));
            }
        }
        return neededSlice;
    }

    /**
     * Compute the infill hatching polygons for this set of patterns
     */
    PolygonList computeInfill(final int stl) {
        return new InFillPatterns().computeHatchedPolygons(stl, layerRules, this);
    }

    private static void setUpShield(final Purge purge, final List<STLObject> stls, final Preferences preferences) {
        if (!preferences.getPrintSettings().printShield()) {
            return;
        }
        final BoundingBox boxWithoutShield = getBoundingBox(stls);
        final double modelZMax = boxWithoutShield.getZint().high();
        final String material = preferences.getAllMaterials()[0];
        final STLObject shield = new STLObject();
        final Attributes att = shield.addSTL(new File(Preferences.getActiveMachineDir(), "shield.stl"), null, null, null);

        final Vector3d shieldSize = shield.extent();

        final Point2D purgePoint = purge.getPurgeMiddle();
        double xOff = purgePoint.x();
        double yOff = purgePoint.y();

        final double zScale = modelZMax / shieldSize.z;
        final double zOff = 0.5 * (modelZMax - shieldSize.z);

        shield.rScale(zScale, true);

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

        att.setMaterial(material);
        stls.add(0, shield);
    }

    /**
     * Compute the outline polygons for this set of patterns.
     */
    PolygonList computeOutlines(final int stl, final PolygonList hatchedPolygons) {
        // The shapes to outline.
        BooleanGridList slice = slice(stl, layerRules.getModelLayer());

        // Pick out the ones we need to do at this height
        slice = neededThisLayer(slice);

        if (slice.size() <= 0) {
            return new PolygonList();
        }

        final BooleanGridList offBorder = offsetOutline(slice, layerRules, -1);
        final PolygonList borderPolygons = offBorder.borders();

        // If we've got polygons to plot, maybe amend them so they start in the middle 
        // of a hatch (this gives cleaner boundaries).  
        if (borderPolygons != null && borderPolygons.size() > 0) {
            middleStarts(borderPolygons, hatchedPolygons, layerRules, slice);
        }

        return borderPolygons;
    }

    PolygonList computeBrim(final int stl, final int brimLines) {
        final GCodeExtruder extruder = layerRules.getPrinter().getExtruders()[0];
        final double extrusionSize = extruder.getExtrusionSize();

        BooleanGridList slice = neededThisLayer(slice(stl, 0));
        final PolygonList result = new PolygonList();
        result.add(slice.borders());
        for (int line = 1; line < brimLines; line++) {
            final BooleanGridList nextSlice = new BooleanGridList();
            for (int i = 0; i < slice.size(); i++) {
                final BooleanGrid grid = slice.get(i);
                nextSlice.add(grid.createOffsetGrid(extrusionSize));
            }
            slice = nextSlice;
            result.add(slice.borders());
        }

        return result;
    }

    /**
     * Generate a set of pixel-map representations, one for each extruder, for
     * STLObject stl at height z.
     */
    BooleanGridList slice(final int stlIndex, final int layer) {
        if (layer < 0) {
            return new BooleanGridList();
        }

        // Is the result in the cache?  If so, just use that.
        BooleanGridList result = cache.getSlice(layer, stlIndex);
        if (result != null) {
            return result;
        }

        // Haven't got it in the cache, so we need to compute it
        // Anything there?
        if (rectangles.get(stlIndex) == null) {
            return new BooleanGridList();
        }

        // Probably...
        final double z = layerRules.getModelZ(layer) + layerRules.getZStep() * 0.5;
        final GCodeExtruder[] extruders = layerRules.getPrinter().getExtruders();
        result = new BooleanGridList();
        CSG2D csgp = null;
        PolygonList pgl = new PolygonList();
        int extruderID;

        // Bin the edges and CSGs (if any) by extruder ID.
        @SuppressWarnings("unchecked")
        final ArrayList<LineSegment>[] edges = new ArrayList[extruders.length];
        @SuppressWarnings("unchecked")
        final ArrayList<CSG3D>[] csgs = new ArrayList[extruders.length];
        final Attributes[] atts = new Attributes[extruders.length];

        for (extruderID = 0; extruderID < extruders.length; extruderID++) {
            if (extruders[extruderID].getID() != extruderID) {
                LOGGER.error("slice(): extruder " + extruderID + "out of sequence: " + extruders[extruderID].getID());
            }
            edges[extruderID] = new ArrayList<LineSegment>();
            csgs[extruderID] = new ArrayList<CSG3D>();
        }

        // Generate all the edges for STLObject i at this z
        final STLObject stlObject = stlsToBuild.get(stlIndex);
        final Transform3D trans = stlObject.getTransform();
        final Matrix4d m4 = new Matrix4d();
        trans.get(m4);

        for (int i = 0; i < stlObject.getCount(); i++) {
            final BranchGroup bg1 = stlObject.getSTL(i);
            final Attributes attr = (Attributes) (bg1.getUserData());
            atts[layerRules.getPrinter().getExtruder(attr.getMaterial()).getID()] = attr;
            final CSG3D csg = stlObject.getCSG(i);
            if (csg != null) {
                csgs[layerRules.getPrinter().getExtruder(attr.getMaterial()).getID()].add(csg.transform(m4));
            } else {
                recursiveSetEdges(bg1, trans, z, attr, edges[layerRules.getPrinter().getExtruder(attr.getMaterial()).getID()]);
            }
        }

        // Turn them into lists of polygons, one for each extruder, then
        // turn those into pixelmaps.
        for (extruderID = 0; extruderID < edges.length; extruderID++) {
            // Deal with CSG shapes (much simpler and faster).
            for (int i = 0; i < csgs[extruderID].size(); i++) {
                csgp = CSG3D.slice(csgs[extruderID].get(i), z);
                result.add(new BooleanGrid(csgp, rectangles.get(stlIndex), atts[extruderID]));
            }

            // Deal with STL-generated edges
            if (edges[extruderID].size() > 0) {
                pgl = simpleCull(edges[extruderID]);

                if (pgl.size() > 0) {
                    // Remove wrinkles
                    pgl = pgl.simplify(preferences.gridResultion() * 1.5);

                    // Fix small radii
                    pgl = arcCompensate(pgl);

                    csgp = pgl.toCSG();

                    // We use the plan rectangle of the entire stl object to store the bitmap, even though this slice may be
                    // much smaller than the whole.  This allows booleans on slices to be computed much more
                    // quickly as each is in the same rectangle so the bit patterns match exactly.  But it does use more memory.
                    result.add(new BooleanGrid(csgp, rectangles.get(stlIndex), pgl.polygon(0).getAttributes()));
                }
            }
        }

        cache.setSlice(result, layer, stlIndex);
        return result;
    }

    /**
     * Add the edge where the plane z cuts the triangle (p, q, r) (if it does).
     * Also update the triangulation of the object below the current slice used
     * for the simulation window.
     */
    private void addEdge(final Point3d p, final Point3d q, final Point3d r, final double z, final Attributes att,
            final ArrayList<LineSegment> edges) {
        Point3d odd = null, even1 = null, even2 = null;
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
            LOGGER.error("addEdge(): the | function doesn't seem to work...");
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
        edges.add(new LineSegment(e1, e2, att));
    }

    /**
     * Run through a Shape3D and set edges from it at plane z Apply the
     * transform first
     */
    private void addAllEdges(final Shape3D shape, final Transform3D trans, final double z, final Attributes att,
            final ArrayList<LineSegment> edges) {
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
            addEdge(q1, q2, q3, z, att, edges);
        }
    }

    /**
     * Unpack the Shape3D(s) from value and set edges from them
     */
    private void recursiveSetEdges(final Object value, final Transform3D trans, final double z, final Attributes att,
            final ArrayList<LineSegment> edges) {
        if (value instanceof SceneGraphObject) {
            final SceneGraphObject sg = (SceneGraphObject) value;
            if (sg instanceof Group) {
                final Group g = (Group) sg;
                final java.util.Enumeration<?> enumKids = g.getAllChildren();
                while (enumKids.hasMoreElements()) {
                    recursiveSetEdges(enumKids.nextElement(), trans, z, att, edges);
                }
            } else if (sg instanceof Shape3D) {
                addAllEdges((Shape3D) sg, trans, z, att, edges);
            }
        }
    }

    /**
     * This assumes that the PolygonList for which it is called is all the
     * closed outline polygons, and that hatching is their infill hatch. It goes
     * through the outlines and the hatch modifying both so that that outlines
     * actually start and end half-way along a hatch line (that half of the
     * hatch line being deleted). When the outlines are then printed, they start
     * and end in the middle of a solid area, thus minimising dribble.
     * 
     * The outline polygons are re-ordered before the start so that their first
     * point is the most extreme one in the current hatch direction.
     * 
     * Only hatches and outlines whose physical extruders match are altered.
     */
    private static void middleStarts(final PolygonList list, final PolygonList hatching, final LayerRules lc,
            final BooleanGridList slice) {
        for (int i = 0; i < list.size(); i++) {
            Polygon outline = list.polygon(i);
            final GCodePrinter printer = lc.getPrinter();
            final GCodeExtruder ex = printer.getExtruder(outline.getAttributes().getMaterial());
            if (ex.getMiddleStart()) {
                Line l = lc.getHatchDirection(false, ex.getExtrusionSize()).pLine();
                if (i % 2 != 0 ^ lc.getMachineLayer() % 4 > 1) {
                    l = l.neg();
                }
                outline = outline.newStart(outline.maximalVertex(l));

                final Point2D start = outline.point(0);
                final PolygonIndexedPoint pp = hatching.ppSearch(start, printer, ex.getPhysicalExtruderNumber());
                boolean failed = true;
                if (pp != null) {
                    pp.findLongEnough(10, 30);
                    final int st = pp.near();
                    final int en = pp.end();
                    final Polygon pg = pp.polygon();

                    // Check that the line from the start of the outline polygon to the first point
                    // of the tail-in is in solid.  If not, we have jumped between polygons and don't
                    // want to use that as a lead in.
                    final Point2D pDif = Point2D.sub(pg.point(st), start);
                    final Point2D pq1 = Point2D.add(start, Point2D.mul(0.25, pDif));
                    final Point2D pq2 = Point2D.add(start, Point2D.mul(0.5, pDif));
                    final Point2D pq3 = Point2D.add(start, Point2D.mul(0.5, pDif));

                    if (slice.membership(pq1) & slice.membership(pq2) & slice.membership(pq3)) {
                        outline.add(start);

                        if (en >= st) {
                            for (int j = st; j <= en; j++) {
                                outline.add(0, pg.point(j)); // Put it on the beginning...
                                if (j < en) {
                                    outline.add(pg.point(j)); // ...and the end.
                                }
                            }
                        } else {
                            for (int j = st; j >= en; j--) {
                                outline.add(0, pg.point(j));
                                if (j > en) {
                                    outline.add(pg.point(j));
                                }
                            }
                        }
                        list.set(i, outline);
                        hatching.cutPolygon(pp.pIndex(), st, en);
                        failed = false;
                    }
                }
                if (failed) {
                    list.set(i, outline.randomStart()); // Best we can do.
                }
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
        final Attributes attributes = polygon.getAttributes();
        final GCodeExtruder e = layerRules.getPrinter().getExtruder(attributes.getMaterial());

        // Multiply the geometrically correct result by factor
        final double factor = e.getArcCompensationFactor();
        if (factor < Constants.TINY_VALUE) {
            return polygon;
        }

        // The points making the arc must be closer than this together
        final double shortSides = e.getArcShortSides();
        final double thickness = e.getExtrusionSize();
        final Polygon result = new Polygon(attributes, polygon.isClosed());
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
            final boolean support) {
        final PrintSettings printSettings = Preferences.getInstance().getPrintSettings();
        final PolygonList result = new PolygonList();
        for (int i = 0; i < list.size(); i++) {
            final BooleanGrid grid = list.get(i);
            final Attributes att = grid.attribute();
            final GCodeExtruder e = layerConditions.getPrinter().getExtruder(att.getMaterial());
            final double infillWidth;
            if (support) {
                infillWidth = printSettings.getSupportSpacing();
            } else if (surface) {
                infillWidth = e.getExtrusionSize();
            } else {
                infillWidth = e.getExtrusionSize() / printSettings.getFillDensity();
            }
            final HalfPlane hatchLine = layerConditions.getHatchDirection(support, infillWidth);
            result.add(grid.hatch(hatchLine, infillWidth, att));
        }
        return result;
    }

    private static BooleanGridList offsetOutline(final BooleanGridList gridList, final LayerRules lc, final double multiplier) {
        final BooleanGridList result = new BooleanGridList();
        for (int i = 0; i < gridList.size(); i++) {
            final BooleanGrid grid = gridList.get(i);
            final BooleanGridList offset = offsetOutline(grid, lc, multiplier);
            if (lc.getPrinter().getExtruder(grid.attribute().getMaterial()).getInsideOut()) {
                offset.reverse();
            }
            for (int j = 0; j < offset.size(); j++) {
                result.add(offset.get(j));
            }
        }
        return result;
    }

    private static BooleanGridList offsetOutline(final BooleanGrid grid, final LayerRules lc, final double multiplier) {
        final Attributes att = grid.attribute();
        if (att == null) {
            throw new RuntimeException("grid attribute is null");
        }
        final GCodeExtruder e = lc.getPrinter().getExtruder(att.getMaterial());
        final BooleanGridList result = new BooleanGridList();
        final int shells = Preferences.getInstance().getPrintSettings().getVerticalShells();
        for (int shell = 0; shell < shells; shell++) {
            final double extrusionSize = e.getExtrusionSize();
            final double offset = multiplier * (shell + 0.5) * extrusionSize;
            final BooleanGrid thisOne = grid.createOffsetGrid(offset);
            if (thisOne.isEmpty()) {
                break;
            } else {
                result.add(thisOne);
            }
        }
        return result;
    }

    static BooleanGridList offset(final BooleanGridList gridList, final LayerRules lc, final double multiplier) {
        final BooleanGridList result = new BooleanGridList();
        for (int i = 0; i < gridList.size(); i++) {
            final BooleanGrid grid = gridList.get(i);
            final Attributes att = grid.attribute();
            if (att == null) {
                throw new RuntimeException("grid attribute is null");
            }
            final GCodeExtruder e = lc.getPrinter().getExtruder(att.getMaterial());
            final double extrusionSize = e.getExtrusionSize();
            final double infillOverlap = e.getInfillOverlap();
            final int shells = Preferences.getInstance().getPrintSettings().getVerticalShells();
            // Must be a hatch.  Only do it if the gap is +ve or we're building the foundation
            final double offSize;
            if (multiplier < 0) {
                offSize = multiplier * (shells + 0.5) * extrusionSize + infillOverlap;
            } else {
                offSize = multiplier * (shells + 0.5) * extrusionSize;
            }
            result.add(grid.createOffsetGrid(offSize));
        }
        return result;
    }

    private static void copyAllStls(final AllSTLsToBuild allStls, final List<STLObject> stlList) {
        for (int i = 0; i < allStls.size(); i++) {
            stlList.add(allStls.get(i));
        }
    }
}
