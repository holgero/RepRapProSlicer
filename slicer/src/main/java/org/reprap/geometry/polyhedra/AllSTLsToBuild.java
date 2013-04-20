package org.reprap.geometry.polyhedra;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
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
import org.reprap.gcode.GCodeExtruder;
import org.reprap.geometry.LayerRules;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.BooleanGridList;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Circle;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.Line;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;
import org.reprap.geometry.polygons.PolygonIndexedPoint;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.graphicio.RFO;
import org.reprap.gui.RepRapBuild;

/**
 * This class holds a list of STLObjects that represents everything that is to
 * be built.
 * 
 * An STLObject may consist of items from several STL files, possible of
 * different materials. But they are all tied together relative to each other in
 * space.
 * 
 * @author Adrian
 */
public class AllSTLsToBuild {
    private static final Logger LOGGER = LogManager.getLogger(AllSTLsToBuild.class);

    /**
     * Ring buffer cache to hold previously computed slices for doing infill and
     * support material calculations.
     * 
     * @author ensab
     */
    private final class SliceCache {
        private final BooleanGridList[][] sliceRing;
        private final BooleanGridList[][] supportRing;
        private final int[] layerNumber;
        private int ringPointer;
        private final int noLayer = Integer.MIN_VALUE;
        private int ringSize = 10;

        private SliceCache(final LayerRules lr) {
            if (lr == null) {
                LOGGER.error("SliceCache(): null LayerRules!");
            }
            ringSize = lr.sliceCacheSize();
            sliceRing = new BooleanGridList[ringSize][stls.size()];
            supportRing = new BooleanGridList[ringSize][stls.size()];
            layerNumber = new int[ringSize];
            ringPointer = 0;
            for (int layer = 0; layer < ringSize; layer++) {
                for (int stl = 0; stl < stls.size(); stl++) {
                    sliceRing[layer][stl] = null;
                    supportRing[layer][stl] = null;
                    layerNumber[layer] = noLayer;
                }
            }
        }

        private int getTheRingLocationForWrite(final int layer) {
            for (int i = 0; i < ringSize; i++) {
                if (layerNumber[i] == layer) {
                    return i;
                }
            }

            final int rp = ringPointer;
            for (int s = 0; s < stls.size(); s++) {
                sliceRing[rp][s] = null;
                supportRing[rp][s] = null;
            }
            ringPointer++;
            if (ringPointer >= ringSize) {
                ringPointer = 0;
            }
            return rp;
        }

        private void setSlice(final BooleanGridList slice, final int layer, final int stl) {
            final int rp = getTheRingLocationForWrite(layer);
            layerNumber[rp] = layer;
            sliceRing[rp][stl] = slice;
        }

        private void setSupport(final BooleanGridList support, final int layer, final int stl) {
            final int rp = getTheRingLocationForWrite(layer);
            layerNumber[rp] = layer;
            supportRing[rp][stl] = support;
        }

        private int getTheRingLocationForRead(final int layer) {
            int rp = ringPointer;
            for (int i = 0; i < ringSize; i++) {
                rp--;
                if (rp < 0) {
                    rp = ringSize - 1;
                }
                if (layerNumber[rp] == layer) {
                    return rp;
                }
            }
            return -1;
        }

        private BooleanGridList getSlice(final int layer, final int stl) {
            final int rp = getTheRingLocationForRead(layer);
            if (rp >= 0) {
                return sliceRing[rp][stl];
            }
            return null;
        }

        private BooleanGridList getSupport(final int layer, final int stl) {
            final int rp = getTheRingLocationForRead(layer);
            if (rp >= 0) {
                return supportRing[rp][stl];
            }
            return null;
        }
    }

    /**
     * The list of things to be built
     */
    private List<STLObject> stls;

    /**
     * The building layer rules
     */
    private LayerRules layerRules = null;

    /**
     * A plan box round each item
     */
    private List<Rectangle> rectangles;

    /**
     * New list of things to be built for reordering
     */
    private List<STLObject> newstls;

    /**
     * The XYZ box around everything
     */
    private BoundingBox XYZbox;

    /**
     * Is the list editable?
     */
    private boolean frozen;

    /**
     * Recently computed slices
     */
    private SliceCache cache;

    private final Preferences preferences = Preferences.getInstance();

    public AllSTLsToBuild() {
        stls = new ArrayList<STLObject>();
        rectangles = null;
        newstls = null;
        XYZbox = null;
        frozen = false;
        cache = null;
        layerRules = null;
    }

    public void add(final STLObject s) {
        if (frozen) {
            LOGGER.error("AllSTLsToBuild.add(): adding an item to a frozen list.");
        }
        stls.add(s);
    }

    /**
     * Add a new STLObject somewhere in the list
     */
    public void add(final int index, final STLObject s) {
        if (frozen) {
            LOGGER.error("AllSTLsToBuild.add(): adding an item to a frozen list.");
        }
        stls.add(index, s);
    }

    /**
     * Add a new collection
     */
    public void add(final AllSTLsToBuild a) {
        if (frozen) {
            LOGGER.error("AllSTLsToBuild.add(): adding a collection to a frozen list.");
        }
        for (int i = 0; i < a.size(); i++) {
            stls.add(a.get(i));
        }
    }

    /**
     * Get the i-th STLObject
     */
    public STLObject get(final int i) {
        return stls.get(i);
    }

    public void remove(final int i) {
        if (frozen) {
            LOGGER.error("AllSTLsToBuild.remove(): removing an item from a frozen list.");
        }
        stls.remove(i);
    }

    /**
     * Find an object in the list
     */
    private int findSTL(final STLObject st) {
        if (size() <= 0) {
            LOGGER.error("AllSTLsToBuild.findSTL(): no objects to pick from!");
            return -1;
        }
        int index = -1;
        for (int i = 0; i < size(); i++) {
            if (get(i) == st) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            LOGGER.error("AllSTLsToBuild.findSTL(): dud object submitted.");
            return -1;
        }
        return index;
    }

    /**
     * Find an object in the list and return the next one.
     */
    public STLObject getNextOne(final STLObject st) {
        int index = findSTL(st);
        index++;
        if (index >= size()) {
            index = 0;
        }
        return get(index);
    }

    /**
     * Return the number of objects.
     */
    public int size() {
        return stls.size();
    }

    /**
     * Create an OpenSCAD (http://www.openscad.org/) program that will read
     * everything in in the same pattern as it is stored here. It can then be
     * written by OpenSCAD as a single STL.
     */
    private String toSCAD() {
        String result = "union()\n{\n";
        for (int i = 0; i < size(); i++) {
            result += get(i).toSCAD();
        }
        result += "}";
        return result;
    }

    /**
     * Write everything to an OpenSCAD program.
     * 
     * @throws IOException
     */
    public void saveSCAD(final File file) throws IOException {
        if (!RFO.checkFile(file)) {
            return;
        }
        final File directory = file.getParentFile();
        if (!directory.exists()) {
            directory.mkdir();
        }
        RFO.copySTLs(this, directory);
        final PrintWriter out = new PrintWriter(new FileWriter(file));
        try {
            out.println(toSCAD());
        } finally {
            out.close();
        }
    }

    /**
     * Reorder the list under user control. The user sends items from the old
     * list one by one. These are added to a new list in that order. When
     * there's only one left that is added last automatically.
     * 
     * Needless to say, this process must be carried through to completion. The
     * function returns true while the process is ongoing, false when it's
     * complete.
     */
    public boolean reorderAdd(final STLObject st) {
        if (frozen) {
            LOGGER.debug("AllSTLsToBuild.reorderAdd(): attempting to reorder a frozen list.");
        }

        if (newstls == null) {
            newstls = new ArrayList<STLObject>();
        }

        final int index = findSTL(st);
        newstls.add(get(index));
        stls.remove(index);

        if (stls.size() > 1) {
            return true;
        }

        newstls.add(get(0));
        stls = newstls;
        newstls = null;
        cache = null; // Just in case...

        return false;
    }

    /**
     * Scan everything loaded and set up the bounding boxes
     */
    public void setBoxes() {
        rectangles = new ArrayList<Rectangle>();
        for (int i = 0; i < stls.size(); i++) {
            rectangles.add(null);
        }

        for (int i = 0; i < stls.size(); i++) {
            final STLObject stl = stls.get(i);
            final Transform3D trans = stl.getTransform();

            final BranchGroup bg = stl.getSTL();

            final java.util.Enumeration<?> enumKids = bg.getAllChildren();

            while (enumKids.hasMoreElements()) {
                final Object ob = enumKids.nextElement();

                if (ob instanceof BranchGroup) {
                    final BranchGroup bg1 = (BranchGroup) ob;
                    final Attributes att = (Attributes) (bg1.getUserData());
                    if (XYZbox == null) {
                        XYZbox = BBox(att.getPart(), trans);
                        if (rectangles.get(i) == null) {
                            rectangles.set(i, new Rectangle(XYZbox.XYbox));
                        } else {
                            rectangles.set(i, Rectangle.union(rectangles.get(i), XYZbox.XYbox));
                        }
                    } else {
                        final BoundingBox s = BBox(att.getPart(), trans);
                        if (s != null) {
                            XYZbox.expand(s);
                            if (rectangles.get(i) == null) {
                                rectangles.set(i, new Rectangle(s.XYbox));
                            } else {
                                rectangles.set(i, Rectangle.union(rectangles.get(i), s.XYbox));
                            }
                        }
                    }
                }
            }
            if (rectangles.get(i) == null) {
                LOGGER.error("AllSTLsToBuild:ObjectPlanRectangle(): object " + i + " is empty");
            }
        }
    }

    /**
     * Freeze the list - no more editing. Also compute the XY box round
     * everything. Also compute the individual plan boxes round each STLObject.
     */
    private void freeze() {
        if (frozen) {
            return;
        }
        if (layerRules == null) {
            LOGGER.error("AllSTLsToBuild.freeze(): layerRules not set!");
        }
        frozen = true;

        if (cache == null) {
            cache = new SliceCache(layerRules);
        }
        setBoxes();
    }

    /**
     * Run through a Shape3D and find its enclosing XYZ box
     */
    private BoundingBox BBoxPoints(final Shape3D shape, final Transform3D trans) {
        BoundingBox b = null;
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d p1 = new Point3d();
        final Point3d q1 = new Point3d();

        if (g != null) {
            for (int i = 0; i < g.getVertexCount(); i++) {
                g.getCoordinate(i, p1);
                trans.transform(p1, q1);
                if (b == null) {
                    b = new BoundingBox(q1);
                } else {
                    b.expand(q1);
                }
            }
        }
        return b;
    }

    /**
     * Unpack the Shape3D(s) from value and find their enclosing XYZ box
     */
    private BoundingBox BBox(final Object value, final Transform3D trans) {
        BoundingBox b = null;

        if (value instanceof SceneGraphObject) {
            final SceneGraphObject sg = (SceneGraphObject) value;
            if (sg instanceof Group) {
                final Group g = (Group) sg;
                final java.util.Enumeration<?> enumKids = g.getAllChildren();
                while (enumKids.hasMoreElements()) {
                    if (b == null) {
                        b = BBox(enumKids.nextElement(), trans);
                    } else {
                        final BoundingBox s = BBox(enumKids.nextElement(), trans);
                        if (s != null) {
                            b.expand(s);
                        }
                    }
                }
            } else if (sg instanceof Shape3D) {
                b = BBoxPoints((Shape3D) sg, trans);
            }
        }

        return b;
    }

    /**
     * Return the XY box round everything
     */
    public Rectangle ObjectPlanRectangle() {
        if (XYZbox == null) {
            LOGGER.error("AllSTLsToBuild.ObjectPlanRectangle(): null XYZbox!");
        }
        return XYZbox.XYbox;
    }

    /**
     * Find the top of the highest object.
     */
    public double maxZ() {
        if (XYZbox == null) {
            LOGGER.error("AllSTLsToBuild.maxZ(): null XYZbox!");
        }
        return XYZbox.Zint.high();
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
            LOGGER.error("AllSTLsToBuild.startLong(): no edges found!");
            return;
        }
        temp = edges.get(0);
        edges.set(0, edges.get(swap));
        edges.set(swap, temp);
        if (Math.sqrt(d) < preferences.gridResultion()) {
            LOGGER.debug("AllSTLsToBuild.startLong(): edge length: " + Math.sqrt(d) + " is the longest.");
        }
    }

    /**
     * Stitch together the some of the edges to form a polygon.
     */
    private Polygon getNextPolygon(final ArrayList<LineSegment> edges) {
        if (!frozen) {
            LOGGER.error("AllSTLsToBuild:getNextPolygon() called for an unfrozen list!");
            freeze();
        }
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

        LOGGER.debug("AllSTLsToBuild.getNextPolygon(): exhausted edge list!");

        return result;
    }

    /**
     * Get all the polygons represented by the edges.
     */
    private PolygonList simpleCull(final ArrayList<LineSegment> edges) {
        if (!frozen) {
            LOGGER.error("AllSTLsToBuild:simpleCull() called for an unfrozen list!");
            freeze();
        }
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
    public PolygonList computeSupport(final int stl) {
        // No more additions or movements, please
        freeze();

        // We start by computing the union of everything in this layer because
        // that is everywhere that support _isn't_ needed.
        // We give the union the attribute of the first thing found, though
        // clearly it will - in general - represent many different substances.
        // But it's only going to be subtracted from other shapes, so what it's made
        // from doesn't matter.

        final int layer = layerRules.getModelLayer();

        final BooleanGridList thisLayer = slice(stl, layer);

        BooleanGrid unionOfThisLayer;
        Attributes a;

        if (thisLayer.size() > 0) {
            unionOfThisLayer = thisLayer.get(0);
            a = unionOfThisLayer.attribute();
        } else {
            a = stls.get(stl).attributes(0);
            unionOfThisLayer = BooleanGrid.nullBooleanGrid();
        }
        for (int i = 1; i < thisLayer.size(); i++) {
            unionOfThisLayer = BooleanGrid.union(unionOfThisLayer, thisLayer.get(i), a);
        }

        // Expand the union of this layer a bit, so that any support is a little clear of 
        // this layer's boundaries.

        BooleanGridList allThis = new BooleanGridList();
        allThis.add(unionOfThisLayer);
        allThis = offset(allThis, layerRules, true, 2); // 2mm gap is a bit of a hack...
        if (allThis.size() > 0) {
            unionOfThisLayer = allThis.get(0);
        } else {
            unionOfThisLayer = BooleanGrid.nullBooleanGrid();
        }

        // Get the layer above and union it with this layer.  That's what needs
        // support on the next layer down.

        final BooleanGridList previousSupport = cache.getSupport(layer + 1, stl);

        cache.setSupport(BooleanGridList.unions(previousSupport, thisLayer), layer, stl);

        // Now we subtract the union of this layer from all the stuff requiring support in the layer above.

        BooleanGridList support = new BooleanGridList();

        if (previousSupport != null) {
            for (int i = 0; i < previousSupport.size(); i++) {
                final BooleanGrid above = previousSupport.get(i);
                final GCodeExtruder e = layerRules.getPrinter().getExtruder(a.getMaterial()).getSupportExtruder();
                if (e != null) {
                    if (layerRules.extruderLiveThisLayer(e.getID())) {
                        support.add(BooleanGrid.difference(above, unionOfThisLayer, a));
                    }
                }
            }
            support = support.unionDuplicates();
        }

        // Now force the attributes of the support pattern to be the support extruders
        // for all the materials in it.  If the material isn't active in this layer, remove it from the list

        for (int i = 0; i < support.size(); i++) {
            final BooleanGrid grid = support.get(i);
            final GCodeExtruder e = layerRules.getPrinter().getExtruder(grid.attribute().getMaterial()).getSupportExtruder();
            if (e == null) {
                LOGGER.error("AllSTLsToBuild.computeSupport(): null support extruder specified!");
                continue;
            }
            grid.forceAttribute(new Attributes(e.getMaterial(), null, e.getAppearance()));
        }

        return AllSTLsToBuild.hatch(support, layerRules, false, null, true);
    }

    /**
     * Set the building layer rules as soon as we know them
     */
    public void setLayerRules(final LayerRules lr) {
        layerRules = lr;
    }

    /**
     * Select from a slice (allLayer) just those parts of it that will be
     * plotted this layer
     */
    BooleanGridList neededThisLayer(final BooleanGridList allLayer, final boolean infill, final boolean support) {
        final BooleanGridList neededSlice = new BooleanGridList();
        for (int i = 0; i < allLayer.size(); i++) {
            GCodeExtruder e;
            final Attributes attribute = allLayer.get(i).attribute();
            if (infill) {
                e = layerRules.getPrinter().getExtruder(attribute.getMaterial()).getInfillExtruder();
            } else if (support) {
                e = layerRules.getPrinter().getExtruder(attribute.getMaterial()).getSupportExtruder();
            } else {
                e = layerRules.getPrinter().getExtruder(attribute.getMaterial());
            }
            if (e != null) {
                if (layerRules.extruderLiveThisLayer(e.getID())) {
                    neededSlice.add(allLayer.get(i));
                }
            }
        }
        return neededSlice;
    }

    /**
     * Compute the infill hatching polygons for this set of patterns
     */
    public PolygonList computeInfill(final int stl) {
        final InFillPatterns infill = new InFillPatterns();
        freeze();
        return infill.computeHatchedPolygons(stl, layerRules, this);
    }

    public void setUpShield(final RepRapBuild builder) {
        if (frozen) {
            LOGGER.error("AllSTLsToBuild.setUpShield() called when frozen!");
        }

        if (!preferences.loadBool("Shield")) {
            return;
        }

        setBoxes();
        final double modelZMax = maxZ();

        final STLObject s = new STLObject();
        final Attributes att = s.addSTL(new File(Preferences.getActiveMachineDir(), "shield.stl"), null, null, null);

        final Vector3d shieldSize = s.extent();

        final Point2D shieldPos = layerRules.getPurgeMiddle();
        double xOff = shieldPos.x();
        double yOff = shieldPos.y();

        final double zScale = modelZMax / shieldSize.z;
        final double zOff = 0.5 * (modelZMax - shieldSize.z);

        s.rScale(zScale, true);

        if (!layerRules.purgeXOriented()) {
            s.translate(new Vector3d(-0.5 * shieldSize.x, -0.5 * shieldSize.y, 0));
            final Transform3D t3d1 = s.getTransform();
            final Transform3D t3d2 = new Transform3D();
            t3d2.rotZ(0.5 * Math.PI);
            t3d1.mul(t3d2);
            s.setTransform(t3d1);
            s.translate(new Vector3d(yOff, -xOff, zOff));
        } else {
            xOff -= 0.5 * shieldSize.x;
            yOff -= shieldSize.y;
            s.translate(new Vector3d(xOff, yOff, zOff));
        }

        att.setMaterial(preferences.getAllMaterials()[0]);
        builder.anotherSTL(s, att, 0);
    }

    /**
     * Compute the outline polygons for this set of patterns.
     */
    public PolygonList computeOutlines(final int stl, final PolygonList hatchedPolygons) {
        freeze();

        // The shapes to outline.
        BooleanGridList slice = slice(stl, layerRules.getModelLayer());

        // Pick out the ones we need to do at this height
        slice = neededThisLayer(slice, false, false);

        if (slice.size() <= 0) {
            return new PolygonList();
        }

        PolygonList borderPolygons;

        // Are we building the raft under things?  If so, there is no border.
        if (layerRules.getLayingSupport()) {
            borderPolygons = null;
        } else {
            final BooleanGridList offBorder = offset(slice, layerRules, true, -1);
            borderPolygons = offBorder.borders();
        }

        // If we've got polygons to plot, maybe amend them so they start in the middle 
        // of a hatch (this gives cleaner boundaries).  
        if (borderPolygons != null && borderPolygons.size() > 0) {
            AllSTLsToBuild.middleStarts(borderPolygons, hatchedPolygons, layerRules, slice);
        }

        return borderPolygons;
    }

    /**
     * Generate a set of pixel-map representations, one for each extruder, for
     * STLObject stl at height z.
     */
    BooleanGridList slice(final int stlIndex, final int layer) {
        if (!frozen) {
            LOGGER.error("AllSTLsToBuild.slice() called when unfrozen!");
            freeze();
        }

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
                LOGGER.error("AllSTLsToBuild.slice(): extruder " + extruderID + "out of sequence: "
                        + extruders[extruderID].getID());
            }
            edges[extruderID] = new ArrayList<LineSegment>();
            csgs[extruderID] = new ArrayList<CSG3D>();
        }

        // Generate all the edges for STLObject i at this z
        final STLObject stlObject = stls.get(stlIndex);
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
                recursiveSetEdges(attr.getPart(), trans, z, attr, edges[layerRules.getPrinter().getExtruder(attr.getMaterial())
                        .getID()]);
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
     * This assumes that the RrPolygonList for which it is called is all the
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
    public static void middleStarts(final PolygonList list, final PolygonList hatching, final LayerRules lc,
            final BooleanGridList slice) {
        for (int i = 0; i < list.size(); i++) {
            Polygon outline = list.polygon(i);
            final GCodeExtruder ex = lc.getPrinter().getExtruder(outline.getAttributes().getMaterial());
            if (ex.getMiddleStart()) {
                Line l = lc.getHatchDirection(ex, false).pLine();
                if (i % 2 != 0 ^ lc.getMachineLayer() % 4 > 1) {
                    l = l.neg();
                }
                outline = outline.newStart(outline.maximalVertex(l));

                final Point2D start = outline.point(0);
                final PolygonIndexedPoint pp = hatching.ppSearch(start,
                        lc.getPrinter().getExtruder(outline.getAttributes().getMaterial()).getPhysicalExtruderNumber());
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
                } catch (final Exception ex) {
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
     * it's false they are in the interior. If overrideDirection is not null,
     * that is used as the hatch direction. Otherwise the hatch is provided by
     * layerConditions.
     */
    public static PolygonList hatch(final BooleanGridList list, final LayerRules layerConditions, final boolean surface,
            final HalfPlane overrideDirection, final boolean support) {
        final PolygonList result = new PolygonList();
        final boolean foundation = layerConditions.getLayingSupport();
        final GCodeExtruder[] es = layerConditions.getPrinter().getExtruders();
        for (int i = 0; i < list.size(); i++) {
            GCodeExtruder e;
            final BooleanGrid grid = list.get(i);
            Attributes att = grid.attribute();
            if (foundation) {
                e = es[0]; // Extruder 0 is used for foundations
            } else {
                e = layerConditions.getPrinter().getExtruder(att.getMaterial());
            }
            GCodeExtruder ei;
            if (!surface) {
                ei = e.getInfillExtruder();
                if (ei != null) {
                    att = new Attributes(ei.getMaterial(), null, ei.getAppearance());
                }
            } else {
                ei = e;
            }
            if (ei != null) {
                HalfPlane hatchLine;
                if (overrideDirection != null) {
                    hatchLine = overrideDirection;
                } else {
                    hatchLine = layerConditions.getHatchDirection(ei, support);
                }
                result.add(grid.hatch(hatchLine, layerConditions.getHatchWidth(ei), att));

            }
        }
        return result;
    }

    /**
     * Offset all the shapes in the list for this layer
     */
    public static BooleanGridList offset(final BooleanGridList gridList, final LayerRules lc, final boolean outline,
            final double multiplier) {
        final boolean foundation = lc.getLayingSupport();
        if (outline && foundation) {
            LOGGER.error("Offsetting a foundation outline!");
        }

        BooleanGridList result = new BooleanGridList();
        for (int i = 0; i < gridList.size(); i++) {
            final BooleanGrid grid = gridList.get(i);
            final Attributes att = grid.attribute();
            if (att == null) {
                LOGGER.error("BooleanGridList.offset(): null attribute!");
            } else {
                final GCodeExtruder[] es = lc.getPrinter().getExtruders();
                GCodeExtruder e;
                int shells;
                if (foundation) {
                    e = es[0]; // By convention extruder 0 builds the foundation
                    shells = 1;
                } else {
                    e = lc.getPrinter().getExtruder(att.getMaterial());
                    shells = e.getShells();
                }
                if (outline) {
                    int shell = 0;
                    boolean carryOn = true;
                    while (carryOn && shell < shells) {
                        final double d = multiplier * (shell + 0.5) * e.getExtrusionSize();
                        final BooleanGrid thisOne = grid.offset(d);
                        if (thisOne.isEmpty()) {
                            carryOn = false;
                        } else {
                            if (shell == 0 && e.getSingleLine()) {
                                final BooleanGrid lines = grid.lines(thisOne, d);
                                lines.setThin(true);
                                result.add(lines);
                            }
                            result.add(thisOne);
                        }
                        shell++;
                    }
                    if (e.getInsideOut()) {
                        result = result.reverse(); // Plot from the inside out?
                    }
                } else {
                    // Must be a hatch.  Only do it if the gap is +ve or we're building the foundation
                    double offSize;
                    final int ei = e.getInfillExtruderNumber();
                    GCodeExtruder ife = e;
                    if (ei >= 0) {
                        ife = es[ei];
                    }
                    if (foundation) {
                        offSize = 3;
                    } else if (multiplier < 0) {
                        offSize = multiplier * (shells + 0.5) * e.getExtrusionSize() + ife.getInfillOverlap();
                    } else {
                        offSize = multiplier * (shells + 0.5) * e.getExtrusionSize();
                    }
                    if (e.getExtrusionInfillWidth() > 0 || foundation) {
                        result.add(grid.offset(offSize));
                    }
                }
            }
        }
        return result;
    }

}
