package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.reprap.Attributes;
import org.reprap.Preferences;
import org.reprap.utilities.Debug;

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
    /**
     * Integer 2D point
     * 
     * @author ensab
     */
    private final class Integer2DPoint {
        private int x;
        private int y;

        private Integer2DPoint(final int xa, final int ya) {
            x = xa;
            y = ya;
        }

        /**
         * Copy constructor
         */
        private Integer2DPoint(final Integer2DPoint a) {
            x = a.x;
            y = a.y;
        }

        /**
         * Convert real-world point to integer
         */
        private Integer2DPoint(final Point2D a) {
            x = iScale(a.x()) - rec.swCorner.x;
            y = iScale(a.y()) - rec.swCorner.y;
        }

        /**
         * Are two points the same?
         */
        private boolean coincidesWith(final Integer2DPoint b) {
            return x == b.x && y == b.y;
        }

        /**
         * Vector addition
         */
        private Integer2DPoint add(final Integer2DPoint b) {
            return new Integer2DPoint(x + b.x, y + b.y);
        }

        /**
         * Vector subtraction
         */
        private Integer2DPoint sub(final Integer2DPoint b) {
            return new Integer2DPoint(x - b.x, y - b.y);
        }

        /**
         * Opposite direction
         */
        private Integer2DPoint neg() {
            return new Integer2DPoint(-x, -y);
        }

        /**
         * Absolute value
         */
        private Integer2DPoint abs() {
            return new Integer2DPoint(Math.abs(x), Math.abs(y));
        }

        /**
         * Squared length
         */
        private long magnitude2() {
            return x * x + y * y;
        }

        @Override
        public String toString() {
            return ": " + x + ", " + y + " :";
        }
    }

    /**
     * Holds rectangles represented by the sw point and the size.
     * 
     */
    private final class iRectangle {
        private Integer2DPoint swCorner;
        private Integer2DPoint size;

        /**
         * Construct from the corner points
         */
        private iRectangle(final Integer2DPoint min, final Integer2DPoint max) {
            swCorner = new Integer2DPoint(min);
            size = max.sub(min);
            size.x++;
            size.y++;
        }

        /**
         * Copy constructor
         */
        private iRectangle(final iRectangle r) {
            swCorner = new Integer2DPoint(r.swCorner);
            size = new Integer2DPoint(r.size);
        }

        /**
         * Useful to have a single-pixel at the origin
         */
        private iRectangle() {
            swCorner = new Integer2DPoint(0, 0);
            size = new Integer2DPoint(1, 1);
        }

        /**
         * Are two rectangles the same?
         */
        private boolean coincidesWith(final iRectangle b) {
            return swCorner.coincidesWith(b.swCorner) && size.coincidesWith(b.size);
        }

        /**
         * This rectangle in the real world
         */
        private Rectangle realRectangle() {
            final Integer2DPoint r = new Integer2DPoint(swCorner.x + size.x - 1, swCorner.y + size.y - 1);
            return new Rectangle(realPoint(swCorner), realPoint(r));
        }

        /**
         * Big rectangle containing the union of two.
         */
        private iRectangle union(final iRectangle b) {
            final iRectangle result = new iRectangle(this);
            result.swCorner.x = Math.min(result.swCorner.x, b.swCorner.x);
            result.swCorner.y = Math.min(result.swCorner.y, b.swCorner.y);
            int sx = result.swCorner.x + result.size.x - 1;
            sx = Math.max(sx, b.swCorner.x + b.size.x - 1) - result.swCorner.x + 1;
            int sy = result.swCorner.y + result.size.y - 1;
            sy = Math.max(sy, b.swCorner.y + b.size.y - 1) - result.swCorner.y + 1;
            result.size = new Integer2DPoint(sx, sy);
            return result;
        }

        /**
         * Rectangle containing the intersection of two.
         */
        private iRectangle intersection(final iRectangle b) {
            final iRectangle result = new iRectangle(this);
            result.swCorner.x = Math.max(result.swCorner.x, b.swCorner.x);
            result.swCorner.y = Math.max(result.swCorner.y, b.swCorner.y);
            int sx = result.swCorner.x + result.size.x - 1;
            sx = Math.min(sx, b.swCorner.x + b.size.x - 1) - result.swCorner.x + 1;
            int sy = result.swCorner.y + result.size.y - 1;
            sy = Math.min(sy, b.swCorner.y + b.size.y - 1) - result.swCorner.y + 1;
            result.size = new Integer2DPoint(sx, sy);
            return result;
        }

        /**
         * Grow (dist +ve) or shrink (dist -ve).
         */
        private iRectangle offset(final int dist) {
            final iRectangle result = new iRectangle(this);
            result.swCorner.x = result.swCorner.x - dist;
            result.swCorner.y = result.swCorner.y - dist;
            result.size.x = result.size.x + 2 * dist;
            result.size.y = result.size.y + 2 * dist;
            return result;
        }

        /**
         * Anything there?
         */
        private boolean isEmpty() {
            return size.x < 0 | size.y < 0;
        }

        private Point2D realPoint(final Integer2DPoint point) {
            return new Point2D(scale(swCorner.x + point.x), scale(swCorner.y + point.y));
        }
    }

    /**
     * Integer-point polygon
     */
    private final class iPolygon {
        /**
         * Auto-extending list of points
         */
        private List<Integer2DPoint> points = null;

        /**
         * Does the polygon loop back on itself?
         */
        private final boolean closed;

        private iPolygon(final boolean c) {
            points = new ArrayList<Integer2DPoint>();
            closed = c;
        }

        /**
         * Deep copy
         */
        private iPolygon(final iPolygon a) {
            points = new ArrayList<Integer2DPoint>();
            for (int i = 0; i < a.size(); i++) {
                add(a.point(i));
            }
            closed = a.closed;
        }

        /**
         * Return the point at a given index
         */
        private Integer2DPoint point(final int i) {
            return points.get(i);
        }

        /**
         * How many points?
         */
        private int size() {
            return points.size();
        }

        /**
         * Add a new point on the end
         */
        private void add(final Integer2DPoint p) {
            points.add(p);
        }

        /**
         * Add a whole polygon on the end
         */
        private void add(final iPolygon a) {
            for (int i = 0; i < a.size(); i++) {
                add(a.point(i));
            }
        }

        /**
         * Delete a point and close the resulting gap
         */
        private void remove(final int i) {
            points.remove(i);
        }

        /**
         * Find the index of the point in the polygon nearest to another point
         * as long as it's less than tooFar2. Set that to Long.MAX_VALUE for a
         * complete search.
         */
        private int nearest(final Integer2DPoint a, final long tooFar2) {
            int i = 0;
            int j = -1;
            long d0 = tooFar2;
            while (i < size()) {
                final long d1 = point(i).sub(a).magnitude2();
                if (d1 < d0) {
                    j = i;
                    d0 = d1;
                }
                i++;
            }
            return j;
        }

        /**
         * Negate (i.e. reverse cyclic order)
         */
        private iPolygon negate() {
            final iPolygon result = new iPolygon(closed);
            for (int i = size() - 1; i >= 0; i--) {
                result.add(point(i));
            }
            return result;
        }

        /**
         * Translate by vector t
         */
        private iPolygon translate(final Integer2DPoint t) {
            final iPolygon result = new iPolygon(closed);
            for (int i = 0; i < size(); i++) {
                result.add(point(i).add(t));
            }
            return result;
        }

        /**
         * Find the farthest point from point v1 on the polygon such that the
         * polygon between the two can be approximated by a DDA straight line
         * from v1.
         */
        private int findAngleStart(final int v1) {
            int top = size() - 1;
            int bottom = v1;
            final Integer2DPoint p1 = point(v1);
            int offCount = 0;
            while (top - bottom > 1) {
                final int middle = (bottom + top) / 2;
                final DDA line = new DDA(p1, point(middle));
                Integer2DPoint n = line.next();
                offCount = 0;
                int j = v1;

                while (j <= middle && n != null && offCount < 2) {
                    if (point(j).coincidesWith(n)) {
                        offCount = 0;
                    } else {
                        offCount++;
                    }
                    n = line.next();
                    j++;
                }

                if (offCount < 2) {
                    bottom = middle;
                } else {
                    top = middle;
                }
            }
            if (offCount < 2) {
                return top;
            } else {
                return bottom;
            }
        }

        /**
         * Generate an equivalent polygon with fewer vertices by removing chains
         * of points that lie in straight lines.
         */
        private iPolygon simplify() {
            if (size() <= 3) {
                return new iPolygon(this);
            }
            final iPolygon r = new iPolygon(closed);
            int v = 0;
            do {
                r.add(point(v));
                v = findAngleStart(v);
            } while (v < size() - 1);
            r.add(point(v));
            return r;
        }

        /**
         * Convert the polygon into a polygon in the real world.
         */
        private Polygon realPolygon(final Attributes a) {
            final Polygon result = new Polygon(a, closed);
            for (int i = 0; i < size(); i++) {
                final Integer2DPoint r = point(i);
                result.add(rec.realPoint(r));
            }
            return result;
        }
    }

    /**
     * A list of polygons
     */
    private static final class iPolygonList {
        private List<iPolygon> polygons = null;

        private iPolygonList() {
            polygons = new ArrayList<iPolygon>();
        }

        /**
         * Return the ith polygon
         */
        private iPolygon polygon(final int i) {
            return polygons.get(i);
        }

        /**
         * How many polygons are there in the list?
         */
        private int size() {
            return polygons.size();
        }

        /**
         * Add a polygon on the end
         */
        private void add(final iPolygon p) {
            polygons.add(p);
        }

        /**
         * Replace a polygon in the list
         */
        private void set(final int i, final iPolygon p) {
            polygons.set(i, p);
        }

        /**
         * Get rid of a polygon from the list
         */
        private void remove(final int i) {
            polygons.remove(i);
        }

        /**
         * Translate by vector t
         */
        private iPolygonList translate(final Integer2DPoint t) {
            final iPolygonList result = new iPolygonList();
            for (int i = 0; i < size(); i++) {
                result.add(polygon(i).translate(t));
            }
            return result;
        }

        /**
         * Turn all the polygons into real-world polygons
         */
        private PolygonList realPolygons(final Attributes a) {
            final PolygonList result = new PolygonList();
            for (int i = 0; i < size(); i++) {
                result.add(polygon(i).realPolygon(a));
            }
            return result;
        }

        /**
         * Simplify all the polygons
         */
        private iPolygonList simplify() {
            final iPolygonList result = new iPolygonList();
            for (int i = 0; i < size(); i++) {
                result.add(polygon(i).simplify());
            }
            return result;
        }
    }

    /**
     * Straight-line DDA
     * 
     * @author ensab
     */
    private final class DDA {
        private final Integer2DPoint delta;
        private Integer2DPoint count;
        private final Integer2DPoint p;
        private final int steps;
        private int taken;
        private final boolean xPlus, yPlus;
        private boolean finished;

        /**
         * Set up the DDA between a start and an end point
         */
        private DDA(final Integer2DPoint s, final Integer2DPoint e) {
            delta = e.sub(s).abs();

            steps = Math.max(delta.x, delta.y);
            taken = 0;

            xPlus = e.x >= s.x;
            yPlus = e.y >= s.y;

            count = new Integer2DPoint(-steps / 2, -steps / 2);

            p = new Integer2DPoint(s);

            finished = false;
        }

        /**
         * Return the next point along the line, or null if the last point
         * returned was the final one.
         */
        private Integer2DPoint next() {
            if (finished) {
                return null;
            }

            final Integer2DPoint result = new Integer2DPoint(p);

            finished = taken >= steps;

            if (!finished) {
                taken++;
                count = count.add(delta);

                if (count.x > 0) {
                    count.x -= steps;
                    if (xPlus) {
                        p.x++;
                    } else {
                        p.x--;
                    }
                }

                if (count.y > 0) {
                    count.y -= steps;
                    if (yPlus) {
                        p.y++;
                    } else {
                        p.y--;
                    }
                }
            }

            return result;
        }
    }

    /**
     * Holds the ends of hatching patterns. Snakes are a combination of the
     * hatching lines that infill a shape plus the bits of boundary that join
     * their ends to make a zig-zag pattern.
     * 
     * @author ensab
     */
    private static final class SnakeEnd {
        private final iPolygon track;
        private final int hitPlaneIndex;

        private SnakeEnd(final iPolygon t, final int h) {
            track = t;
            hitPlaneIndex = h;
        }
    }

    /**
     * The resolution of the RepRap machine
     */
    private static final double pixSize = Preferences.machineResolution() * 0.6;
    private static final double realResolution = pixSize * 1.5;
    private static final double rSwell = 0.5; // mm by which to swell rectangles to give margins round stuff

    /**
     * How simple does a CSG expression have to be to not be worth pruning
     * further?
     */
    private static final int simpleEnough = 3;

    private static final BooleanGrid nothingThere = new BooleanGrid();

    /**
     * Run round the eight neighbours of a pixel anticlockwise from bottom left
     */
    private final Integer2DPoint[] neighbour = { new Integer2DPoint(-1, -1), //0 /
            new Integer2DPoint(0, -1), //1 V
            new Integer2DPoint(1, -1), //2 \
            new Integer2DPoint(1, 0), //3 ->
            new Integer2DPoint(1, 1), //4 /
            new Integer2DPoint(0, 1), //5 ^
            new Integer2DPoint(-1, 1), //6 \
            new Integer2DPoint(-1, 0) //7 <
    };

    // Marching squares directions.  2x2 grid bits:
    //
    //    0  1
    //
    //    2  3

    private final int[] march = { -1, // 0
            5, // 1
            3, // 2
            3, // 3
            7, // 4
            5, // 5
            7, // 6
            3, // 7
            1, // 8
            5, // 9
            1, // 10
            1, // 11
            7, // 12
            5, // 13
            7, // 14
            -1 // 15
    };

    /**
     * Lookup table behaves like scalar product for two neighbours i and j; get
     * it by neighbourProduct[Math.abs(j - i)]
     */
    private final int[] neighbourProduct = { 2, 1, 0, -1, -2, -1, 0, 1 };

    /**
     * The pixel map
     */
    private BitSet bits;

    /**
     * Flags for visited pixels during searches
     */
    private BitSet visited;

    /**
     * The rectangle the pixelmap covers
     */
    private iRectangle rec;

    /**
     * The attributes
     */
    private Attributes att;
    private Boolean isThin = false;

    /**
     * Back and forth from real to pixel/integer coordinates
     */
    private static double scale(final int i) {
        return i * pixSize;
    }

    private static int iScale(final double d) {
        return (int) Math.round(d / pixSize);
    }

    /**
     * Build the grid from a CSG expression
     */
    public BooleanGrid(final CSG2D csgExp, final Rectangle rectangle, final Attributes a) {
        att = a;
        isThin = false;
        final Rectangle ri = rectangle.offset(rSwell);
        rec = new iRectangle(new Integer2DPoint(0, 0), new Integer2DPoint(1, 1)); // Set the origin to (0, 0)...
        rec.swCorner = new Integer2DPoint(ri.sw()); // That then gets subtracted by the iPoint constructor to give the true origin
        rec.size = new Integer2DPoint(ri.ne()); // The true origin is now automatically subtracted.
        bits = new BitSet(rec.size.x * rec.size.y);
        visited = null;
        generateQuadTree(new Integer2DPoint(0, 0), new Integer2DPoint(rec.size.x - 1, rec.size.y - 1), csgExp);
        deWhisker();
    }

    /**
     * Copy constructor N.B. attributes are _not_ deep copied
     */
    private BooleanGrid(final BooleanGrid bg) {
        att = bg.att;
        visited = null;
        isThin = bg.isThin;
        rec = new iRectangle(bg.rec);
        bits = (BitSet) bg.bits.clone();
    }

    /**
     * Copy constructor with new rectangle N.B. attributes are _not_ deep copied
     */
    private BooleanGrid(final BooleanGrid bg, final iRectangle newRec) {
        att = bg.att;
        visited = null;
        isThin = bg.isThin;
        rec = new iRectangle(newRec);
        bits = new BitSet(rec.size.x * rec.size.y);
        final iRectangle recScan = rec.intersection(bg.rec);
        final int offxOut = recScan.swCorner.x - rec.swCorner.x;
        final int offyOut = recScan.swCorner.y - rec.swCorner.y;
        final int offxIn = recScan.swCorner.x - bg.rec.swCorner.x;
        final int offyIn = recScan.swCorner.y - bg.rec.swCorner.y;
        for (int x = 0; x < recScan.size.x; x++) {
            for (int y = 0; y < recScan.size.y; y++) {
                bits.set(pixI(x + offxOut, y + offyOut), bg.bits.get(bg.pixI(x + offxIn, y + offyIn)));
            }
        }
    }

    /**
     * The empty grid
     */
    private BooleanGrid() {
        att = new Attributes(null, null, null, null);
        rec = new iRectangle();
        bits = new BitSet(1);
        isThin = false;
        visited = null;
    }

    /**
     * The empty set
     */
    public static BooleanGrid nullBooleanGrid() {
        return nothingThere;
    }

    /**
     * Overwrite the attributes Only to be used if you know what you're doing...
     */
    public void forceAttribute(final Attributes a) {
        att = a;
    }

    /**
     * The index of a pixel in the 1D bit array.
     */
    private int pixI(final int x, final int y) {
        return x * rec.size.y + y;
    }

    /**
     * The index of a pixel in the 1D bit array.
     */
    private int pixI(final Integer2DPoint p) {
        return pixI(p.x, p.y);
    }

    /**
     * The pixel corresponding to an index into the bit array
     * 
     * @param i
     * @return
     */
    private Integer2DPoint pixel(final int i) {
        return new Integer2DPoint(i / rec.size.y, i % rec.size.y);
    }

    /**
     * Return the attributes
     */
    public Attributes attribute() {
        return att;
    }

    void setThin(final Boolean t) {
        isThin = t;
    }

    /**
     * Any pixels set?
     */
    boolean isEmpty() {
        return bits.isEmpty();
    }

    /**
     * Is a point inside the image?
     */
    private boolean inside(final Integer2DPoint p) {
        if (p.x < 0) {
            return false;
        }
        if (p.y < 0) {
            return false;
        }
        if (p.x >= rec.size.x) {
            return false;
        }
        if (p.y >= rec.size.y) {
            return false;
        }
        return true;
    }

    /**
     * Set pixel p to value v
     */
    private void set(final Integer2DPoint p, final boolean v) {
        if (!inside(p)) {
            Debug.getInstance().errorMessage("BoolenGrid.set(): attempt to set pixel beyond boundary!");
            return;
        }
        bits.set(pixI(p), v);
    }

    /**
     * Fill a disc centre c radius r with v
     */
    private void disc(final Integer2DPoint c, final int r, final boolean v) {
        for (int x = -r; x <= r; x++) {
            final int xp = c.x + x;
            if (xp > 0 && xp < rec.size.x) {
                final int y = (int) Math.round(Math.sqrt((r * r - x * x)));
                int yp0 = c.y - y;
                int yp1 = c.y + y;
                yp0 = Math.max(yp0, 0);
                yp1 = Math.min(yp1, rec.size.y - 1);
                if (yp0 <= yp1) {
                    bits.set(pixI(xp, yp0), pixI(xp, yp1) + 1, v);
                }
            }
        }
    }

    /**
     * Fill a rectangle with centreline running from p0 to p1 of width 2r with v
     */
    private void rectangle(final Integer2DPoint p0, final Integer2DPoint p1, int r, final boolean v) {
        r = Math.abs(r);
        final Point2D rp0 = new Point2D(p0.x, p0.y);
        final Point2D rp1 = new Point2D(p1.x, p1.y);
        final HalfPlane[] h = new HalfPlane[4];
        h[0] = new HalfPlane(rp0, rp1);
        h[2] = h[0].offset(r);
        h[0] = h[0].offset(-r).complement();
        h[1] = new HalfPlane(rp0, Point2D.add(rp0, h[2].normal()));
        h[3] = new HalfPlane(rp1, Point2D.add(rp1, h[0].normal()));
        double xMin = Double.MAX_VALUE;
        double xMax = Double.MIN_VALUE;
        Point2D p = null;
        for (int i = 0; i < 4; i++) {
            try {
                p = h[i].crossPoint(h[(i + 1) % 4]);
            } catch (final Exception e) {
            }
            xMin = Math.min(xMin, p.x());
            xMax = Math.max(xMax, p.x());
        }
        int iXMin = (int) Math.round(xMin);
        iXMin = Math.max(iXMin, 0);
        int iXMax = (int) Math.round(xMax);
        iXMax = Math.min(iXMax, rec.size.x - 1);
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
                yHigh = Math.min(yHigh, rec.size.y - 1);
                if (yLow <= yHigh) {
                    bits.set(pixI(x, yLow), pixI(x, yHigh) + 1, v);
                }
            }
        }
    }

    /**
     * Set a whole rectangle to one value
     */
    private void homogeneous(final Integer2DPoint ipsw, final Integer2DPoint ipne, final boolean v) {
        for (int x = ipsw.x; x <= ipne.x; x++) {
            bits.set(pixI(x, ipsw.y), pixI(x, ipne.y) + 1, v);
        }
    }

    /**
     * Set a whole rectangle to the right values for a CSG expression
     */
    private void heterogeneous(final Integer2DPoint ipsw, final Integer2DPoint ipne, final CSG2D csgExpression) {
        for (int x = ipsw.x; x <= ipne.x; x++) {
            for (int y = ipsw.y; y <= ipne.y; y++) {
                final Integer2DPoint r = new Integer2DPoint(x, y);
                bits.set(pixI(x, y), csgExpression.value(rec.realPoint(r)) <= 0);
            }
        }
    }

    /**
     * The rectangle surrounding the set pixels in real coordinates.
     */
    private Rectangle box() {
        final Integer2DPoint r = new Integer2DPoint(0, 0);
        final Integer2DPoint r1 = new Integer2DPoint(rec.size.x - 1, rec.size.y - 1);
        return new Rectangle(rec.realPoint(r), rec.realPoint(r1));
    }

    /**
     * The value at a point.
     */
    private boolean get(final Integer2DPoint p) {
        if (!inside(p)) {
            return false;
        }
        return bits.get(pixI(p));
    }

    /**
     * Get the value at the point corresponding to somewhere in the real world
     * That is, membership test.
     */
    public boolean get(final Point2D p) {
        return get(new Integer2DPoint(p));
    }

    /**
     * Set a point as visited
     */
    private void vSet(final Integer2DPoint p, final boolean v) {
        if (!inside(p)) {
            Debug.getInstance().errorMessage("BoolenGrid.vSet(): attempt to set pixel beyond boundary!");
            return;
        }
        if (visited == null) {
            visited = new BitSet(rec.size.x * rec.size.y);
        }
        visited.set(pixI(p), v);
    }

    /**
     * Has this point been visited?
     */
    private boolean vGet(final Integer2DPoint p) {
        if (visited == null) {
            return false;
        }
        if (!inside(p)) {
            return false;
        }
        return visited.get(pixI(p));
    }

    /**
     * Find a set point
     */
    private Integer2DPoint findSeed_i() {
        for (int x = 0; x < rec.size.x; x++) {
            for (int y = 0; y < rec.size.y; y++) {
                final Integer2DPoint p = new Integer2DPoint(x, y);
                if (get(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Find a set point
     */
    public Point2D findSeed() {
        final Integer2DPoint p = findSeed_i();
        if (p == null) {
            return null;
        } else {
            return rec.realPoint(p);
        }
    }

    /**
     * Find the centroid of the shape(s)
     */
    private Integer2DPoint findCentroid_i() {
        Integer2DPoint sum = new Integer2DPoint(0, 0);
        int points = 0;
        for (int x = 0; x < rec.size.x; x++) {
            for (int y = 0; y < rec.size.y; y++) {
                final Integer2DPoint p = new Integer2DPoint(x, y);
                if (get(p)) {
                    sum = sum.add(p);
                    points++;
                }
            }
        }
        if (points == 0) {
            return null;
        }
        return new Integer2DPoint(sum.x / points, sum.y / points);
    }

    /**
     * Find the centroid of the shape(s)
     */
    public Point2D findCentroid() {
        final Integer2DPoint p = findCentroid_i();
        if (p == null) {
            return null;
        } else {
            return rec.realPoint(p);
        }
    }

    /**
     * Generate the entire image from a CSG experession recursively using a quad
     * tree.
     */
    private void generateQuadTree(final Integer2DPoint ipsw, final Integer2DPoint ipne, final CSG2D csgExpression) {
        final Point2D inc = new Point2D(pixSize * 0.5, pixSize * 0.5);
        final Point2D p0 = rec.realPoint(ipsw);

        // Single pixel?
        if (ipsw.coincidesWith(ipne)) {
            set(ipsw, csgExpression.value(p0) <= 0);
            return;
        }

        // Uniform rectangle?
        final Point2D p1 = rec.realPoint(ipne);
        final Interval i = csgExpression.value(new Rectangle(Point2D.sub(p0, inc), Point2D.add(p1, inc)));
        if (!i.zero()) {
            homogeneous(ipsw, ipne, i.high() <= 0);
            return;
        }

        // Non-uniform, but simple, rectangle
        if (csgExpression.complexity() <= simpleEnough) {
            heterogeneous(ipsw, ipne, csgExpression);
            return;
        }

        // Divide this rectangle into four (roughly) congruent quads.

        // Work out the corner coordinates.
        final int x0 = ipsw.x;
        final int y0 = ipsw.y;
        final int x1 = ipne.x;
        final int y1 = ipne.y;
        final int xd = (x1 - x0 + 1);
        final int yd = (y1 - y0 + 1);
        int xm = x0 + xd / 2;
        if (xd == 2) {
            xm--;
        }
        int ym = y0 + yd / 2;
        if (yd == 2) {
            ym--;
        }
        Integer2DPoint sw, ne;

        // Special case - a single vertical line of pixels
        if (xd <= 1) {
            if (yd <= 1) {
                Debug.getInstance().errorMessage("BooleanGrid.generateQuadTree: attempt to divide single pixel!");
            }
            sw = new Integer2DPoint(x0, y0);
            ne = new Integer2DPoint(x0, ym);
            generateQuadTree(sw, ne, csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(
                    rec.realPoint(ne), inc))));

            sw = new Integer2DPoint(x0, ym + 1);
            ne = new Integer2DPoint(x0, y1);
            generateQuadTree(sw, ne, csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(
                    rec.realPoint(ne), inc))));

            return;
        }

        // Special case - a single horizontal line of pixels
        if (yd <= 1) {
            sw = new Integer2DPoint(x0, y0);
            ne = new Integer2DPoint(xm, y0);
            generateQuadTree(sw, ne, csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(
                    rec.realPoint(ne), inc))));

            sw = new Integer2DPoint(xm + 1, y0);
            ne = new Integer2DPoint(x1, y0);
            generateQuadTree(sw, ne, csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(
                    rec.realPoint(ne), inc))));

            return;
        }

        // General case - 4 quads.
        sw = new Integer2DPoint(x0, y0);
        ne = new Integer2DPoint(xm, ym);
        generateQuadTree(sw, ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(rec.realPoint(ne), inc))));

        sw = new Integer2DPoint(x0, ym + 1);
        ne = new Integer2DPoint(xm, y1);
        generateQuadTree(sw, ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(rec.realPoint(ne), inc))));

        sw = new Integer2DPoint(xm + 1, ym + 1);
        ne = new Integer2DPoint(x1, y1);
        generateQuadTree(sw, ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(rec.realPoint(ne), inc))));

        sw = new Integer2DPoint(xm + 1, y0);
        ne = new Integer2DPoint(x1, ym);
        generateQuadTree(sw, ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw), inc), Point2D.add(rec.realPoint(ne), inc))));

    }

    /**
     * Reset all the visited flags for the entire image
     */
    private void resetVisited() {
        if (visited != null) {
            visited.clear();
        }
    }

    /**
     * Is a pixel on an edge? If it is solid and there is air at at least one of
     * north, south, east, or west, then yes; otherwise no.
     */
    private boolean isEdgePixel(final Integer2DPoint a) {
        if (!get(a)) {
            return false;
        }

        for (int i = 1; i < 8; i += 2) {
            if (!get(a.add(neighbour[i]))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remove whiskers (single threads of pixels) and similar nasties. TODO:
     * also need to do the same for cracks?
     */
    private void deWhisker() {
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            final Integer2DPoint here = pixel(i);
            if (neighbourCount(here) < 3) {
                set(here, false);
            }
        }

        for (int x = 0; x < rec.size.x - 1; x++) {
            for (int y = 0; y < rec.size.y - 1; y++) {
                final Integer2DPoint start = new Integer2DPoint(x, y);
                final int m = marchPattern(start);
                if (m == 6 || m == 9) {
                    if (poll(start, 3) > 0.5) {
                        set(start, true);
                        set(start.add(neighbour[1]), true);
                        set(start.add(neighbour[2]), true);
                        set(start.add(neighbour[2]), true);
                    } else {
                        set(start, false);
                        set(start.add(neighbour[1]), false);
                        set(start.add(neighbour[2]), false);
                        set(start.add(neighbour[2]), false);
                    }
                }
            }
        }
    }

    /**
     * Look-up table to find the index of a neighbour point, n, from the point.
     */
    private int neighbourIndex(final Integer2DPoint n) {
        switch ((n.y + 1) * 3 + n.x + 1) {
        case 0:
            return 0;
        case 1:
            return 1;
        case 2:
            return 2;
        case 3:
            return 7;
        case 5:
            return 3;
        case 6:
            return 6;
        case 7:
            return 5;
        case 8:
            return 4;
        default:
            Debug.getInstance().errorMessage("BooleanGrid.neighbourIndex(): not a neighbour point!" + n.toString());
        }
        return 0;
    }

    /**
     * Count the solid neighbours of this point
     */
    private int neighbourCount(final Integer2DPoint p) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            if (get(p.add(neighbour[i]))) {
                result++;
            }
        }
        return result;
    }

    /**
     * Find the index of the neighbouring point that's closest to a given real
     * direction.
     */
    private int directionToNeighbour(final Point2D p) {
        double score = Double.NEGATIVE_INFINITY;
        int result = -1;

        for (int i = 0; i < 8; i++) {
            // Can't use neighbour.realPoint as that adds swCorner...
            //  We have to normailze neighbour, to get answers proportional to cosines
            final double s = Point2D.mul(p, new Point2D(neighbour[i].x, neighbour[i].y).norm());
            if (s > score) {
                result = i;
                score = s;
            }
        }
        if (result < 0) {
            Debug.getInstance().errorMessage("BooleanGrid.directionToNeighbour(): scalar product error!" + p.toString());
        }
        return result;
    }

    /**
     * Find a neighbour of a pixel that has not yet been visited, that is on an
     * edge, and that is nearest to a given neighbour direction, nd. If nd < 0
     * the first unvisited neighbour is returned. If no valid neighbour exists,
     * null is returned. This prefers to visit valid pixels with few neighbours,
     * and only after that tries to head in direction nd.
     */
    private Integer2DPoint findUnvisitedNeighbourOnEdgeInDirection(final Integer2DPoint a, final int nd) {
        Integer2DPoint result = null;
        int directionScore = -5;
        int neighbourScore = 9;
        for (int i = 0; i < 8; i++) {
            final Integer2DPoint b = a.add(neighbour[i]);
            if (isEdgePixel(b)) {
                if (!vGet(b)) {
                    if (nd < 0) {
                        return b;
                    }
                    final int ns = neighbourCount(b);
                    if (ns <= neighbourScore) {
                        neighbourScore = ns;
                        final int s = neighbourProduct[Math.abs(nd - i)];
                        if (s > directionScore) {
                            directionScore = s;
                            result = b;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Useful debugging function
     */
    private String printNearby(final Integer2DPoint p, final int b) {
        String op = new String();
        for (int y = p.y + b; y >= p.y - b; y--) {
            for (int x = p.x - b; x <= p.x + b; x++) {
                final Integer2DPoint q = new Integer2DPoint(x, y);
                if (q.coincidesWith(p)) {
                    if (get(p)) {
                        op += " +";
                    } else {
                        op += " o";
                    }
                } else if (get(q)) {
                    if (visited != null) {
                        if (vGet(q)) {
                            op += " v";
                        } else {
                            op += " 1";
                        }
                    } else {
                        op += " 1";
                    }
                } else {
                    op += " .";
                }
            }
            op += "\n";
        }
        return op;
    }

    /**
     * Recursive flood-fill of solid pixels from p to return a BooleanGrid of
     * just the shape connected to that pixel.
     * 
     * @param p
     * @return
     */
    public BooleanGrid floodCopy(final Point2D pp) {
        Integer2DPoint p = new Integer2DPoint(pp);
        if (!inside(p) || !this.get(p)) {
            return nothingThere;
        }
        final BooleanGrid result = new BooleanGrid();
        result.att = att;
        result.visited = null;
        result.rec = new iRectangle(rec);
        result.bits = new BitSet(result.rec.size.x * result.rec.size.y);

        // We implement our own floodfill stack, rather than using recursion to
        // avoid having to specify a big Java stack just for this one function.

        final int top = 400000;
        final Integer2DPoint[] stack = new Integer2DPoint[top];
        int sp = 0;
        stack[sp] = p;
        Integer2DPoint q;

        while (sp > -1) {
            p = stack[sp];
            sp--;

            result.set(p, true);

            for (int i = 1; i < 8; i = i + 2) {
                q = p.add(neighbour[i]);
                if (this.get(q) && !result.get(q)) {
                    sp++;
                    if (sp >= top) {
                        Debug.getInstance().errorMessage("BooleanGrid.floodCopy(): stack overflow!");
                        return result;
                    }
                    stack[sp] = q;
                }
            }
        }

        return result;
    }

    /**
     * Calculate the 4-bit marching squares value for a point
     */
    private int marchPattern(final Integer2DPoint ip) {
        int result = 0;

        if (get(ip)) {
            result |= 1;
        }
        if (get(ip.add(neighbour[3]))) {
            result |= 2;
        }
        if (get(ip.add(neighbour[1]))) {
            result |= 4;
        }
        if (get(ip.add(neighbour[2]))) {
            result |= 8;
        }
        return result;
    }

    /**
     * Return all the outlines of all the solid areas as polygons consisting of
     * all the pixels that make up the outlines.
     */
    private iPolygonList iAllPerimitersRaw() {
        return marchAll();
    }

    /**
     * Return all the outlines of all the solid areas as polygons in their
     * simplest form.
     */
    private iPolygonList iAllPerimiters() {
        return iAllPerimitersRaw().simplify();
    }

    /**
     * Return all the outlines of all the solid areas as real-world polygons
     * with attributes a
     */
    public PolygonList allPerimiters(final Attributes a) {
        PolygonList r = iAllPerimiters().realPolygons(a);
        r = r.simplify(realResolution);
        return r;
    }

    private double poll(final Integer2DPoint p, int b) {
        int result = 0;
        Integer2DPoint q;
        for (int y = p.y + b; y >= p.y - b; y--) {
            for (int x = p.x - b; x <= p.x + b; x++) {
                q = new Integer2DPoint(x, y);
                if (get(q)) {
                    result++;
                }
            }
        }
        b++;
        return (double) result / (double) (b * b);
    }

    /**
     * Run marching squares round the polygon starting with the 2x2 march
     * pattern at start
     */
    private iPolygon marchRound(final Integer2DPoint start) {
        final iPolygon result = new iPolygon(true);

        Integer2DPoint here = new Integer2DPoint(start);
        Integer2DPoint pix;
        int m;
        boolean step = true;

        do {
            m = marchPattern(here);
            switch (m) {
            case 1:
                if (!vGet(here)) {
                    result.add(here);
                    vSet(here, true);
                }
                break;
            case 2:
                pix = here.add(neighbour[3]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 3:
                if (!vGet(here)) {
                    result.add(here);
                    vSet(here, true);
                }
                pix = here.add(neighbour[3]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 4:
                pix = here.add(neighbour[1]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 5:
                if (!vGet(here)) {
                    result.add(here);
                    vSet(here, true);
                }
                pix = here.add(neighbour[1]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 6:
                Debug.getInstance().errorMessage(
                        "BooleanGrid.marchRound() - dud 2x2 grid: " + m + " at " + here.toString() + "\n"
                                + printNearby(here, 4) + "\n\n");
                step = false;
                pix = here.add(neighbour[3]);
                set(pix, false);
                vSet(pix, false);
                pix = here.add(neighbour[1]);
                set(pix, false);
                vSet(pix, false);
                here = result.point(result.size() - 1);
                if (!get(here)) {
                    if (result.size() > 1) {
                        result.remove(result.size() - 1);
                        here = result.point(result.size() - 1);
                        if (!get(here)) {
                            Debug.getInstance().errorMessage(
                                    "BooleanGrid.marchRound() - backtracked to an unfilled point!" + printNearby(here, 4)
                                            + "\n\n");
                            result.remove(result.size() - 1);
                            here = result.point(result.size() - 1);
                        }
                    } else {
                        here = start;
                    }
                }
                break;

            case 7:
                pix = here.add(neighbour[1]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                pix = here.add(neighbour[3]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 8:
                pix = here.add(neighbour[2]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 9:
                Debug.getInstance().errorMessage(
                        "BooleanGrid.marchRound() - dud 2x2 grid: " + m + " at " + here.toString() + "\n"
                                + printNearby(here, 4) + "\n\n");
                step = false;
                set(here, false);
                vSet(here, false);
                pix = here.add(neighbour[2]);
                set(pix, false);
                vSet(pix, false);
                here = result.point(result.size() - 1);
                if (!get(here)) {
                    if (result.size() > 1) {
                        result.remove(result.size() - 1);
                        here = result.point(result.size() - 1);
                        if (!get(here)) {
                            Debug.getInstance().errorMessage(
                                    "BooleanGrid.marchRound() - backtracked to an unfilled point!" + printNearby(here, 4)
                                            + "\n\n");
                            result.remove(result.size() - 1);
                            here = result.point(result.size() - 1);
                        }
                    } else {
                        here = start;
                    }
                }

                break;

            case 10:
                pix = here.add(neighbour[3]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                pix = here.add(neighbour[2]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 11:
                if (!vGet(here)) {
                    result.add(here);
                    vSet(here, true);
                }
                pix = here.add(neighbour[2]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 12:
                pix = here.add(neighbour[2]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                pix = here.add(neighbour[1]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;
            case 13:
                pix = here.add(neighbour[2]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                if (!vGet(here)) {
                    result.add(here);
                    vSet(here, true);
                }
                break;
            case 14:
                pix = here.add(neighbour[3]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                pix = here.add(neighbour[1]);
                if (!vGet(pix)) {
                    result.add(pix);
                    vSet(pix, true);
                }
                break;

            default:
                Debug.getInstance().errorMessage(
                        "BooleanGrid.marchRound() - dud 2x2 grid: " + m + " at " + here.toString() + "\n"
                                + printNearby(here, 4) + "\n\n");
                return result;
            }
            if (step) {
                here = here.add(neighbour[march[m]]);
            }
            step = true;
        } while (!here.coincidesWith(start));

        return result;
    }

    /**
     * Run marching squares round all polygons in the pattern, returning a list
     * of them all
     */
    private iPolygonList marchAll() {
        final iPolygonList result = new iPolygonList();
        if (isEmpty()) {
            return result;
        }
        Integer2DPoint start;
        iPolygon p;
        int m;

        for (int x = 0; x < rec.size.x - 1; x++) {
            for (int y = 0; y < rec.size.y - 1; y++) {
                start = new Integer2DPoint(x, y);
                m = marchPattern(start);
                if (m != 0 && m != 15) {
                    if (!(vGet(start) || vGet(start.add(neighbour[1])) || vGet(start.add(neighbour[2])) || vGet(start
                            .add(neighbour[3])))) {
                        p = marchRound(start);
                        if (p.size() > 2) {
                            result.add(p);
                        }
                    }
                }
            }
        }
        resetVisited();
        return result;
    }

    /**
     * Generate a sequence of point-pairs where the line h enters and leaves
     * solid areas. The point pairs are stored in a polygon, which should
     * consequently have an even number of points in it on return.
     */
    private iPolygon hatch(final HalfPlane h) {
        final iPolygon result = new iPolygon(false);

        final Interval se = box().wipe(h.pLine(), Interval.bigInterval());

        if (se.empty()) {
            return result;
        }

        final Integer2DPoint s = new Integer2DPoint(h.pLine().point(se.low()));
        final Integer2DPoint e = new Integer2DPoint(h.pLine().point(se.high()));
        if (get(s)) {
            Debug.getInstance().errorMessage("BooleanGrid.hatch(): start point is in solid!");
        }
        final DDA dda = new DDA(s, e);

        Integer2DPoint n = dda.next();
        Integer2DPoint nOld = n;
        boolean v;
        boolean vs = false;
        while (n != null) {
            v = get(n);
            if (v != vs) {
                if (v) {
                    result.add(n);
                } else {
                    result.add(nOld);
                }
            }
            vs = v;
            nOld = n;
            n = dda.next();
        }

        if (get(e)) {
            Debug.getInstance().errorMessage("BooleanGrid.hatch(): end point is in solid!");
            result.add(e);
        }

        if (result.size() % 2 != 0) {
            Debug.getInstance().errorMessage("BooleanGrid.hatch(): odd number of crossings: " + result.size());
        }
        return result;
    }

    /**
     * Find the bit of polygon edge between start/originPlane and targetPlane
     * TODO: origin == target!!!
     * 
     * @return polygon edge between start/originaPlane and targetPlane
     */
    private SnakeEnd goToPlane(final Integer2DPoint start, final List<HalfPlane> hatches, final int originP, final int targetP) {
        final iPolygon track = new iPolygon(false);

        final HalfPlane originPlane = hatches.get(originP);
        final HalfPlane targetPlane = hatches.get(targetP);

        int dir = directionToNeighbour(originPlane.normal());

        if (originPlane.value(targetPlane.pLine().origin()) < 0) {
            dir = neighbourIndex(neighbour[dir].neg());
        }

        if (!get(start)) {
            Debug.getInstance().errorMessage("BooleanGrid.goToPlane(): start is not solid!");
            return null;
        }

        final double vTarget = targetPlane.value(rec.realPoint(start));

        vSet(start, true);

        Integer2DPoint p = findUnvisitedNeighbourOnEdgeInDirection(start, dir);
        if (p == null) {
            return null;
        }

        Integer2DPoint pNew;
        final double vOrigin = originPlane.value(rec.realPoint(p));
        boolean notCrossedOriginPlane = originPlane.value(rec.realPoint(p)) * vOrigin >= 0;
        boolean notCrossedTargetPlane = targetPlane.value(rec.realPoint(p)) * vTarget >= 0;
        while (notCrossedOriginPlane && notCrossedTargetPlane) {
            track.add(p);
            vSet(p, true);
            pNew = findUnvisitedNeighbourOnEdgeInDirection(p, dir);
            if (pNew == null) {
                for (int i = 0; i < track.size(); i++) {
                    vSet(track.point(i), false);
                }
                return null;
            }
            dir = neighbourIndex(pNew.sub(p));
            p = pNew;
            notCrossedOriginPlane = originPlane.value(rec.realPoint(p)) * vOrigin >= 0;
            notCrossedTargetPlane = targetPlane.value(rec.realPoint(p)) * vTarget >= 0;
        }

        if (notCrossedOriginPlane) {
            return (new SnakeEnd(track, targetP));
        }

        if (notCrossedTargetPlane) {
            return (new SnakeEnd(track, originP));
        }

        Debug.getInstance().errorMessage("BooleanGrid.goToPlane(): invalid ending!");

        return null;
    }

    /**
     * Find the piece of edge between start and end (if there is one).
     */
    private iPolygon goToPoint(final Integer2DPoint start, final Integer2DPoint end, final HalfPlane hatch, final double tooFar) {
        final iPolygon track = new iPolygon(false);

        Integer2DPoint diff = end.sub(start);
        if (diff.x == 0 && diff.y == 0) {
            track.add(start);
            return track;
        }

        int dir = directionToNeighbour(new Point2D(diff.x, diff.y));

        if (!get(start)) {
            Debug.getInstance().errorMessage("BooleanGrid.goToPlane(): start is not solid!");
            return null;
        }

        vSet(start, true);

        Integer2DPoint p = findUnvisitedNeighbourOnEdgeInDirection(start, dir);
        if (p == null) {
            return null;
        }

        while (true) {
            track.add(p);
            vSet(p, true);
            p = findUnvisitedNeighbourOnEdgeInDirection(p, dir);
            boolean lost = p == null;
            if (!lost) {
                lost = Math.abs(hatch.value(rec.realPoint(p))) > tooFar;
            }
            if (lost) {
                for (int i = 0; i < track.size(); i++) {
                    vSet(track.point(i), false);
                }
                vSet(start, false);
                return null;
            }
            diff = end.sub(p);
            if (diff.magnitude2() < 3) {
                return track;
            }
            dir = directionToNeighbour(new Point2D(diff.x, diff.y));
        }
    }

    /**
     * Take a list of hatch point pairs from hatch (above) and the corresponding
     * lines that created them, and stitch them together to make a weaving
     * snake-like hatching pattern for infill.
     */
    private iPolygon snakeGrow(final iPolygonList ipl, final List<HalfPlane> hatches, int thisHatch, int thisPt) {
        final iPolygon result = new iPolygon(false);

        iPolygon thisPolygon = ipl.polygon(thisHatch);
        Integer2DPoint pt = thisPolygon.point(thisPt);
        result.add(pt);
        SnakeEnd jump;
        do {
            thisPolygon.remove(thisPt);
            if (thisPt % 2 != 0) {
                thisPt--;
            }
            pt = thisPolygon.point(thisPt);
            result.add(pt);
            thisHatch++;
            if (thisHatch < hatches.size()) {
                jump = goToPlane(pt, hatches, thisHatch - 1, thisHatch);
            } else {
                jump = null;
            }
            thisPolygon.remove(thisPt);
            if (jump != null) {
                result.add(jump.track);
                thisHatch = jump.hitPlaneIndex;
                thisPolygon = ipl.polygon(thisHatch);
                thisPt = thisPolygon.nearest(jump.track.point(jump.track.size() - 1), 10);
            }
        } while (jump != null && thisPt >= 0);
        return result;
    }

    /**
     * Fine the nearest plane in the hatch to a given point
     */
    private HalfPlane hPlane(final Integer2DPoint p, final List<HalfPlane> hatches) {
        int bot = 0;
        int top = hatches.size() - 1;
        final Point2D rp = rec.realPoint(p);
        double dbot = Math.abs(hatches.get(bot).value(rp));
        double dtop = Math.abs(hatches.get(top).value(rp));
        while (top - bot > 1) {
            final int mid = (top + bot) / 2;
            if (dbot < dtop) {
                top = mid;
                dtop = Math.abs(hatches.get(top).value(rp));
            } else {
                bot = mid;
                dbot = Math.abs(hatches.get(bot).value(rp));
            }
        }
        if (dtop < dbot) {
            return hatches.get(top);
        } else {
            return hatches.get(bot);
        }
    }

    /**
     * Run through the snakes, trying to join them up to make longer snakes
     */
    private void joinUpSnakes(final iPolygonList snakes, final List<HalfPlane> hatches, final double gap) {
        int i = 0;
        if (hatches.size() <= 0) {
            return;
        }
        final Point2D n = hatches.get(0).normal();
        iPolygon track;
        while (i < snakes.size()) {
            final Integer2DPoint iStart = snakes.polygon(i).point(0);
            final Integer2DPoint iEnd = snakes.polygon(i).point(snakes.polygon(i).size() - 1);
            double d;
            int j = i + 1;
            boolean incrementI = true;
            while (j < snakes.size()) {
                final Integer2DPoint jStart = snakes.polygon(j).point(0);
                final Integer2DPoint jEnd = snakes.polygon(j).point(snakes.polygon(j).size() - 1);
                incrementI = true;

                Point2D diff = Point2D.sub(rec.realPoint(jStart), rec.realPoint(iStart));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iStart, jStart, hPlane(iStart, hatches), gap);
                    if (track != null) {
                        final iPolygon p = snakes.polygon(i).negate();
                        p.add(track);
                        p.add(snakes.polygon(j));
                        snakes.set(i, p);
                        snakes.remove(j);
                        incrementI = false;
                        break;
                    }
                }

                diff = Point2D.sub(rec.realPoint(jEnd), rec.realPoint(iStart));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iStart, jEnd, hPlane(iStart, hatches), gap);
                    if (track != null) {
                        final iPolygon p = snakes.polygon(j);
                        p.add(track.negate());
                        p.add(snakes.polygon(i));
                        snakes.set(i, p);
                        snakes.remove(j);
                        incrementI = false;
                        break;
                    }
                }

                diff = Point2D.sub(rec.realPoint(jStart), rec.realPoint(iEnd));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iEnd, jStart, hPlane(iEnd, hatches), gap);
                    if (track != null) {
                        final iPolygon p = snakes.polygon(i);
                        p.add(track);
                        p.add(snakes.polygon(j));
                        snakes.set(i, p);
                        snakes.remove(j);
                        incrementI = false;
                        break;
                    }
                }

                diff = Point2D.sub(rec.realPoint(jEnd), rec.realPoint(iEnd));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iEnd, jEnd, hPlane(iEnd, hatches), gap);
                    if (track != null) {
                        final iPolygon p = snakes.polygon(i);
                        p.add(track);
                        p.add(snakes.polygon(j).negate());
                        snakes.set(i, p);
                        snakes.remove(j);
                        incrementI = false;
                        break;
                    }
                }
                j++;
            }
            if (incrementI) {
                i++;
            }
        }
    }

    /**
     * Hatch all the polygons parallel to line hp with increment gap
     * 
     * @return a polygon list of hatch lines as the result with attributes a
     */
    public PolygonList hatch(final HalfPlane hp, final double gap, final Attributes a) {
        if (gap <= 0) {
            return new PolygonList();
        }

        final Rectangle big = box().scale(1.1);
        final double d = Math.sqrt(big.dSquared());

        final Point2D orth = hp.normal();

        final int quadPointing = (int) (2 + 2 * Math.atan2(orth.y(), orth.x()) / Math.PI);

        Point2D org = big.ne();

        switch (quadPointing) {
        case 1:
            org = big.nw();
            break;

        case 2:
            org = big.sw();
            break;

        case 3:
            org = big.se();
            break;

        case 0:
        default:
            break;
        }

        double dist = Point2D.mul(org, orth) / gap;
        dist = (1 + (long) dist) * gap;
        HalfPlane hatcher = new HalfPlane(hp);
        hatcher = hatcher.offset(dist);

        final List<HalfPlane> hatches = new ArrayList<HalfPlane>();
        final iPolygonList iHatches = new iPolygonList();

        double g = 0;
        while (g < d) {
            final iPolygon ip = hatch(hatcher);

            if (ip.size() > 0) {
                hatches.add(hatcher);
                iHatches.add(ip);
            }
            hatcher = hatcher.offset(gap);
            g += gap;
        }

        // Now we have the individual hatch lines, join them up
        final iPolygonList snakes = new iPolygonList();
        int segment;
        do {
            segment = -1;
            for (int i = 0; i < iHatches.size(); i++) {
                if ((iHatches.polygon(i)).size() > 0) {
                    segment = i;
                    break;
                }
            }
            if (segment >= 0) {
                snakes.add(snakeGrow(iHatches, hatches, segment, 0));
            }
        } while (segment >= 0);

        try {
            if (Preferences.loadGlobalBool("PathOptimise")) {
                joinUpSnakes(snakes, hatches, gap);
            }
        } catch (final Exception e) {
        }

        resetVisited();

        return snakes.realPolygons(a).simplify(realResolution);
    }

    /**
     * This assumes that shrunk is this bitmap offset by dist from a previous
     * calculation. It grows shrunk by -dist, then subtracts that from itself.
     * The result is a bitmap of all the thin lines in this pattern that were
     * discarded by the original offset (plus some noise at places square convex
     * corners that have grown back rounded).
     */
    BooleanGrid lines(final BooleanGrid shrunk, final double dist) {
        if (dist >= 0) {
            Debug.getInstance().errorMessage("BooleanGrid.lines() called with non-negative offset: " + dist);
            return new BooleanGrid();
        }
        return difference(this, shrunk.offset(-dist));
    }

    /**
     * Offset the pattern by a given real-world distance. If the distance is
     * negative the pattern is shrunk; if it is positive it is grown;
     */
    BooleanGrid offset(final double dist) {
        final int r = iScale(dist);

        final BooleanGrid result = new BooleanGrid(this, rec.offset(r));
        if (r == 0) {
            return result;
        }

        final iPolygonList polygons = iAllPerimiters().translate(rec.swCorner.sub(result.rec.swCorner));
        if (polygons.size() <= 0) {
            final iRectangle newRec = new iRectangle(result.rec);
            newRec.size.x = 1;
            newRec.size.y = 1;
            return new BooleanGrid(CSG2D.nothing(), newRec.realRectangle(), att);
        }

        for (int p = 0; p < polygons.size(); p++) {
            final iPolygon ip = polygons.polygon(p);
            for (int e = 0; e < ip.size(); e++) {
                final Integer2DPoint p0 = ip.point(e);
                final Integer2DPoint p1 = ip.point((e + 1) % ip.size());
                result.rectangle(p0, p1, Math.abs(r), r > 0);
                result.disc(p1, Math.abs(r), r > 0);
            }
        }
        if (result.isEmpty()) {
            return nothingThere;
        }
        //if(dist < 0)
        result.deWhisker();
        return result;
    }

    /**
     * Compute the union of two bit patterns, forcing attribute a on the result.
     */
    public static BooleanGrid union(final BooleanGrid d, final BooleanGrid e, final Attributes a) {
        BooleanGrid result;

        if (d == nothingThere) {
            if (e == nothingThere) {
                return nothingThere;
            }
            if (e.att == a) {
                return e;
            }
            result = new BooleanGrid(e);
            result.forceAttribute(a);
            return result;
        }

        if (e == nothingThere) {
            if (d.att == a) {
                return d;
            }
            result = new BooleanGrid(d);
            result.forceAttribute(a);
            return result;
        }

        if (d.rec.coincidesWith(e.rec)) {
            result = new BooleanGrid(d);
            result.bits.or(e.bits);
        } else {
            final iRectangle u = d.rec.union(e.rec);
            result = new BooleanGrid(d, u);
            final BooleanGrid temp = new BooleanGrid(e, u);
            result.bits.or(temp.bits);
        }
        result.forceAttribute(a);
        return result;
    }

    /**
     * Compute the union of two bit patterns
     */
    static BooleanGrid union(final BooleanGrid d, final BooleanGrid e) {
        final BooleanGrid result = union(d, e, d.att);
        if (result != nothingThere && d.att != e.att) {
            Debug.getInstance().errorMessage(
                    "BooleanGrid.union(): attempt to union two bitmaps of different materials: " + d.attribute().getMaterial()
                            + " and " + e.attribute().getMaterial());
        }
        return result;
    }

    /**
     * Compute the intersection of two bit patterns
     */
    private static BooleanGrid intersection(final BooleanGrid d, final BooleanGrid e, final Attributes a) {
        BooleanGrid result;

        if (d == nothingThere || e == nothingThere) {
            return nothingThere;
        }

        if (d.rec.coincidesWith(e.rec)) {
            result = new BooleanGrid(d);
            result.bits.and(e.bits);
        } else {

            final iRectangle u = d.rec.intersection(e.rec);
            if (u.isEmpty()) {
                return nothingThere;
            }
            result = new BooleanGrid(d, u);
            final BooleanGrid temp = new BooleanGrid(e, u);
            result.bits.and(temp.bits);
        }
        if (result.isEmpty()) {
            return nothingThere;
        }
        result.deWhisker();
        result.forceAttribute(a);
        return result;
    }

    /**
     * Compute the intersection of two bit patterns
     */
    public static BooleanGrid intersection(final BooleanGrid d, final BooleanGrid e) {
        final BooleanGrid result = intersection(d, e, d.att);
        if (result != nothingThere && d.att != e.att) {
            Debug.getInstance().errorMessage(
                    "BooleanGrid.intersection(): attempt to intersect two bitmaps of different materials: "
                            + d.attribute().getMaterial() + " and " + e.attribute().getMaterial());
        }
        return result;
    }

    /**
     * Grid d - grid e, forcing attribute a on the result d's rectangle is
     * presumed to contain the result. TODO: write a function to compute the
     * rectangle from the bitmap
     */
    public static BooleanGrid difference(final BooleanGrid d, final BooleanGrid e, final Attributes a) {
        if (d == nothingThere) {
            return nothingThere;
        }

        BooleanGrid result;

        if (e == nothingThere) {
            if (d.att == a) {
                return d;
            }
            result = new BooleanGrid(d);
            result.forceAttribute(a);
            return result;
        }

        result = new BooleanGrid(d);
        BooleanGrid temp;
        if (d.rec.coincidesWith(e.rec)) {
            temp = e;
        } else {
            temp = new BooleanGrid(e, result.rec);
        }
        result.bits.andNot(temp.bits);
        if (result.isEmpty()) {
            return nothingThere;
        }
        result.deWhisker();
        result.forceAttribute(a);
        return result;
    }

    /**
     * Grid d - grid e d's rectangle is presumed to contain the result. TODO:
     * write a function to compute the rectangle from the bitmap
     */
    public static BooleanGrid difference(final BooleanGrid d, final BooleanGrid e) {
        final BooleanGrid result = difference(d, e, d.att);
        if (result != nothingThere && d.att != e.att) {
            Debug.getInstance().errorMessage(
                    "BooleanGrid.difference(): attempt to subtract two bitmaps of different materials: "
                            + d.attribute().getMaterial() + " and " + e.attribute().getMaterial());
        }
        return result;
    }
}
