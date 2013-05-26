package org.reprap.geometry.polygons;

import java.util.BitSet;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class stores a rectangular grid at the same grid resolution as the
 * RepRap machine's finest resolution using the Java BitSet class.
 * 
 * There are two types of pixel: solid (or true), and air (or false).
 * 
 * There are Boolean operators implemented to allow unions, intersections, and
 * differences of two bitmaps, and complements of one.
 * 
 * There are also functions to do ray-trace intersections, to find the parts of
 * lines that are solid, and outline following, to find the perimiters of solid
 * shapes as polygons.
 * 
 * The class makes extensive use of lazy evaluation.
 * 
 * @author Adrian Bowyer
 */
public class BooleanGrid {
    private static final Logger LOGGER = LogManager.getLogger(BooleanGrid.class);
    public static final BooleanGrid NOTHING_THERE = new BooleanGrid(0.0, null, new Integer2DRectangle(), new BitSet(1));

    private final double pixelSize;
    private final BitSet bits;
    private final Integer2DRectangle rectangle;
    private String material;

    private BooleanGrid(final double pixelSize, final String material, final Integer2DRectangle rectangle, final BitSet bits) {
        this.pixelSize = pixelSize;
        this.material = material;
        this.rectangle = rectangle;
        this.bits = bits;
    }

    BooleanGrid(final double pixelSize, final String material, final Integer2DRectangle rectangle) {
        this(pixelSize, material, rectangle, new BitSet(rectangle.getSizeX() * rectangle.getSizeY()));
    }

    /**
     * Copy constructor
     */
    BooleanGrid(final BooleanGrid bg) {
        this(bg.pixelSize, bg.material, new Integer2DRectangle(bg.rectangle), (BitSet) bg.bits.clone());
    }

    /**
     * Build the grid from a CSG expression
     */
    public BooleanGrid(final double pixelSize, final String material, final Rectangle realRectangle, final CSG2D csgExp) {
        this(pixelSize, material, new Integer2DRectangle(realRectangle.offset(0.5), pixelSize));
        new Csg2dGridPainter(pixelSize, rectangle, bits).paint(csgExp);
    }

    /**
     * Copy constructor with new rectangle
     */
    BooleanGrid(final BooleanGrid bg, final Integer2DRectangle newRec) {
        this(bg.pixelSize, bg.material, new Integer2DRectangle(newRec));
        final Integer2DRectangle recScan = rectangle.intersection(bg.rectangle);
        final int offxOut = recScan.getSwCorner().getX() - rectangle.getSwCorner().getX();
        final int offyOut = recScan.getSwCorner().getY() - rectangle.getSwCorner().getY();
        final int offxIn = recScan.getSwCorner().getX() - bg.rectangle.getSwCorner().getX();
        final int offyIn = recScan.getSwCorner().getY() - bg.rectangle.getSwCorner().getY();
        for (int x = 0; x < recScan.getSizeX(); x++) {
            for (int y = 0; y < recScan.getSizeY(); y++) {
                bits.set(pixI(x + offxOut, y + offyOut), bg.bits.get(bg.pixI(x + offxIn, y + offyIn)));
            }
        }
    }

    public void setMaterial(final String material) {
        this.material = material;
    }

    /**
     * The index of a pixel in the 1D bit array.
     */
    private int pixI(final int x, final int y) {
        return x * rectangle.getSizeY() + y;
    }

    /**
     * The index of a pixel in the 1D bit array.
     */
    int pixI(final Integer2DPoint p) {
        return pixI(p.getX(), p.getY());
    }

    public String getMaterial() {
        return material;
    }

    /**
     * Any pixels set?
     */
    public boolean isEmpty() {
        return bits.isEmpty();
    }

    /**
     * Is a point inside the image?
     */
    boolean inside(final Integer2DPoint p) {
        return inside(p.getX(), p.getY());
    }

    private boolean inside(final int x, final int y) {
        if (x < 0) {
            return false;
        }
        if (y < 0) {
            return false;
        }
        if (x >= rectangle.getSizeX()) {
            return false;
        }
        if (y >= rectangle.getSizeY()) {
            return false;
        }
        return true;
    }

    /**
     * Set pixel p to value v
     */
    void set(final Integer2DPoint p, final boolean v) {
        if (!inside(p)) {
            LOGGER.error("BoolenGrid.set(): attempt to set pixel beyond boundary!");
            return;
        }
        bits.set(pixI(p), v);
    }

    /**
     * Fill a disc centre c radius r with v
     */
    private void disc(final Integer2DPoint c, final int r, final boolean v) {
        for (int x = -r; x <= r; x++) {
            final int xp = c.getX() + x;
            if (xp > 0 && xp < rectangle.getSizeX()) {
                final int y = (int) Math.round(Math.sqrt((r * r - x * x)));
                int yp0 = c.getY() - y;
                int yp1 = c.getY() + y;
                yp0 = Math.max(yp0, 0);
                yp1 = Math.min(yp1, rectangle.getSizeY() - 1);
                if (yp0 <= yp1) {
                    bits.set(pixI(xp, yp0), pixI(xp, yp1) + 1, v);
                }
            }
        }
    }

    /**
     * Fill a rectangle with centreline running from p0 to p1 of width 2r with v
     */
    private void rectangle(final Integer2DPoint p0, final Integer2DPoint p1, final int r, final boolean v) {
        final int halfFillWidth = Math.abs(r);
        final Point2D rp0 = new Point2D(p0.getX(), p0.getY());
        final Point2D rp1 = new Point2D(p1.getX(), p1.getY());
        final HalfPlane[] h = new HalfPlane[4];
        h[0] = new HalfPlane(rp0, rp1);
        h[2] = h[0].offset(halfFillWidth);
        h[0] = h[0].offset(-halfFillWidth).complement();
        h[1] = new HalfPlane(rp0, Point2D.add(rp0, h[2].normal()));
        h[3] = new HalfPlane(rp1, Point2D.add(rp1, h[0].normal()));
        double xMin = Double.MAX_VALUE;
        double xMax = Double.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            try {
                final Point2D p = h[i].crossPoint(h[(i + 1) % 4]);
                xMin = Math.min(xMin, p.x());
                xMax = Math.max(xMax, p.x());
            } catch (final ParallelException e) {
                LOGGER.catching(Level.DEBUG, e);
            }
        }
        int iXMin = (int) Math.round(xMin);
        iXMin = Math.max(iXMin, 0);
        int iXMax = (int) Math.round(xMax);
        iXMax = Math.min(iXMax, rectangle.getSizeX() - 1);
        for (int x = iXMin; x <= iXMax; x++) {
            final Line yLine = new Line(new Point2D(x, 0), new Point2D(x, 1));
            Interval iv = Interval.bigInterval();
            for (int i = 0; i < 4; i++) {
                iv = h[i].wipe(yLine, iv);
            }
            if (!iv.empty()) {
                int yLow = (int) Math.round(yLine.point(iv.low()).y());
                int yHigh = (int) Math.round(yLine.point(iv.high()).y());
                yLow = Math.max(yLow, 0);
                yHigh = Math.min(yHigh, rectangle.getSizeY() - 1);
                if (yLow <= yHigh) {
                    bits.set(pixI(x, yLow), pixI(x, yHigh) + 1, v);
                }
            }
        }
    }

    /**
     * The value at a point.
     */
    boolean get(final Integer2DPoint p) {
        if (!inside(p)) {
            return false;
        }
        return bits.get(pixI(p));
    }

    /**
     * The value at a point (in internal integer coordinates).
     */
    boolean get(final int x, final int y) {
        if (!inside(x, y)) {
            return false;
        }
        return bits.get(pixI(x, y));
    }

    /**
     * Get the value at the point corresponding to somewhere in the real world
     * That is, membership test.
     */
    public boolean get(final Point2D p) {
        return get(rectangle.convertToInteger2DPoint(p, pixelSize));
    }

    /**
     * Find the centroid of the shape(s)
     */
    private Integer2DPoint findCentroid_i() {
        Integer2DPoint sum = new Integer2DPoint(0, 0);
        int points = 0;
        for (int x = 0; x < rectangle.getSizeX(); x++) {
            for (int y = 0; y < rectangle.getSizeY(); y++) {
                if (get(x, y)) {
                    sum = sum.add(new Integer2DPoint(x, y));
                    points++;
                }
            }
        }
        if (points == 0) {
            return null;
        }
        return new Integer2DPoint(sum.getX() / points, sum.getY() / points);
    }

    /**
     * Find the centroid of the shape(s)
     */
    public Point2D findCentroid() {
        final Integer2DPoint p = findCentroid_i();
        if (p == null) {
            return null;
        } else {
            return rectangle.realPoint(p, pixelSize);
        }
    }

    /**
     * Return all the outlines of all the solid areas as polygons in their
     * simplest form.
     */
    private Integer2DPolygonList iAllPerimiters() {
        final BooleanGridWalker gridWalker = new BooleanGridWalker(this);
        final Integer2DPolygonList polygons = gridWalker.marchAll();
        return polygons.simplify();
    }

    /**
     * Return all the outlines of all the solid areas as real-world polygons.
     */
    public PolygonList allPerimiters() {
        PolygonList r = iAllPerimiters().realPolygons(getMaterial(), rectangle, pixelSize);
        r = r.simplify(1.5 * pixelSize);
        return r;
    }

    /**
     * Offset the pattern by a given real-world distance. If the distance is
     * negative the pattern is shrunk; if it is positive it is grown;
     */
    public BooleanGrid createOffsetGrid(final double dist) {
        final int r = (int) Math.round(dist / pixelSize);
        if (r == 0) {
            return new BooleanGrid(this);
        }
        final BooleanGrid result;
        if (r > 0) {
            result = new BooleanGrid(this, rectangle.offset(r));
        } else {
            result = new BooleanGrid(this);
        }
        result.offsetOutlines(r);
        if (result.isEmpty()) {
            return NOTHING_THERE;
        }
        return result;
    }

    private void offsetOutlines(final int r) {
        final Integer2DPolygonList polygons = iAllPerimiters();
        for (int p = 0; p < polygons.size(); p++) {
            final Integer2DPolygon ip = polygons.polygon(p);
            for (int e = 0; e < ip.size(); e++) {
                final Integer2DPoint p0 = ip.point(e);
                final Integer2DPoint p1 = ip.point((e + 1) % ip.size());
                rectangle(p0, p1, Math.abs(r), r > 0);
                disc(p1, Math.abs(r), r > 0);
            }
        }
    }

    public BooleanGrid subtractPolygons(final PolygonList polygons, final double width) {
        final BooleanGrid result = new BooleanGrid(this);
        for (int i = 0; i < polygons.size(); i++) {
            final Polygon polygon = polygons.polygon(i);
            result.subtract(polygon, width);
        }
        return result;
    }

    private void subtract(final Polygon polygon, final double width) {
        final int pixelWidth = (int) Math.round(width / pixelSize);
        for (int i = 0; i < polygon.size(); i++) {
            final Integer2DPoint start = rectangle.convertToInteger2DPoint(polygon.point(i), pixelSize);
            final Integer2DPoint end = rectangle.convertToInteger2DPoint(polygon.point((i + 1) % polygon.size()), pixelSize);
            rectangle(start, end, pixelWidth, false);
            rectangle(end.add(new Integer2DPoint(-pixelWidth, 0)), end.add(new Integer2DPoint(pixelWidth, 0)), pixelWidth,
                    false);
        }
    }

    void unionWith(final BooleanGrid other) {
        bits.or(other.bits);
    }

    void substract(final BooleanGrid other) {
        bits.andNot(other.bits);
    }

    public void intersectWith(final BooleanGrid other) {
        bits.and(other.bits);
    }

    double getPixelSize() {
        return pixelSize;
    }

    Integer2DRectangle getRectangle() {
        return rectangle;
    }
}
