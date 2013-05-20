package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

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
    private static final double SWELL_AMOUNT = 0.5; // mm by which to swell rectangles to give margins round stuff

    /**
     * How simple does a CSG expression have to be to not be worth pruning
     * further?
     */
    private static final int SIMPLE_ENOUGH = 3;
    private static final BooleanGrid NOTHING_THERE = new BooleanGrid(0.0);

    /**
     * Run round the eight neighbours of a pixel anticlockwise from bottom left
     */
    private static final Integer2DPoint[] NEIGHBOUR = { new Integer2DPoint(-1, -1), //0 /
            new Integer2DPoint(0, -1), //1 V
            new Integer2DPoint(1, -1), //2 \
            new Integer2DPoint(1, 0), //3 ->
            new Integer2DPoint(1, 1), //4 /
            new Integer2DPoint(0, 1), //5 ^
            new Integer2DPoint(-1, 1), //6 \
            new Integer2DPoint(-1, 0) //7 <
    };

    /**
     * Lookup table behaves like scalar product for two neighbours i and j; get
     * it by neighbourProduct[Math.abs(j - i)]
     */
    private static final int[] NEIGHBOUR_PRODUCT = { 2, 1, 0, -1, -2, -1, 0, 1 };

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
    private Integer2DRectangle rec;
    private String material;

    private final double pixelSize;

    /**
     * Build the grid from a CSG expression
     */
    public BooleanGrid(final CSG2D csgExp, final Rectangle rectangle, final String material, final double pixelSize) {
        this.material = material;
        this.pixelSize = pixelSize;
        final Rectangle ri = rectangle.offset(SWELL_AMOUNT);
        rec = new Integer2DRectangle(new Integer2DPoint(0, 0), new Integer2DPoint(1, 1)); // Set the origin to (0, 0)...
        rec.swCorner = rec.convertToInteger2DPoint(ri.sw(), pixelSize); // That then gets subtracted by the iPoint constructor to give the true origin
        rec.size = rec.convertToInteger2DPoint(ri.ne(), pixelSize); // The true origin is now automatically subtracted.
        bits = new BitSet(rec.size.x * rec.size.y);
        visited = null;
        generateQuadTree(new Integer2DPoint(0, 0), new Integer2DPoint(rec.size.x - 1, rec.size.y - 1), csgExp);
        deWhisker();
    }

    /**
     * Copy constructor N.B. attributes are _not_ deep copied
     */
    private BooleanGrid(final BooleanGrid bg, final double pixelSize) {
        this.pixelSize = pixelSize;
        material = bg.material;
        visited = null;
        rec = new Integer2DRectangle(bg.rec);
        bits = (BitSet) bg.bits.clone();
    }

    /**
     * Copy constructor with new rectangle N.B. attributes are _not_ deep copied
     */
    private BooleanGrid(final BooleanGrid bg, final Integer2DRectangle newRec, final double pixelSize) {
        this.pixelSize = pixelSize;
        material = bg.material;
        visited = null;
        rec = new Integer2DRectangle(newRec);
        bits = new BitSet(rec.size.x * rec.size.y);
        final Integer2DRectangle recScan = rec.intersection(bg.rec);
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
    private BooleanGrid(final double pixelSize) {
        this.pixelSize = pixelSize;
        material = null;
        rec = new Integer2DRectangle();
        bits = new BitSet(1);
        visited = null;
    }

    /**
     * The empty set
     */
    public static BooleanGrid nullBooleanGrid() {
        return NOTHING_THERE;
    }

    public void setMaterial(final String material) {
        this.material = material;
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
    private boolean inside(final Integer2DPoint p) {
        return inside(p.x, p.y);
    }

    private boolean inside(final int x, final int y) {
        if (x < 0) {
            return false;
        }
        if (y < 0) {
            return false;
        }
        if (x >= rec.size.x) {
            return false;
        }
        if (y >= rec.size.y) {
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
    private void rectangle(final Integer2DPoint p0, final Integer2DPoint p1, final int r, final boolean v) {
        final int halfFillWidth = Math.abs(r);
        final Point2D rp0 = new Point2D(p0.x, p0.y);
        final Point2D rp1 = new Point2D(p1.x, p1.y);
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
                bits.set(pixI(x, y), csgExpression.value(rec.realPoint(r, pixelSize)) <= 0);
            }
        }
    }

    /**
     * The rectangle surrounding the set pixels in real coordinates.
     */
    private Rectangle box() {
        final Integer2DPoint r = new Integer2DPoint(0, 0);
        final Integer2DPoint r1 = new Integer2DPoint(rec.size.x - 1, rec.size.y - 1);
        return new Rectangle(rec.realPoint(r, pixelSize), rec.realPoint(r1, pixelSize));
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
        return get(rec.convertToInteger2DPoint(p, pixelSize));
    }

    /**
     * Set a point as visited
     */
    private void vSet(final Integer2DPoint p, final boolean v) {
        if (!inside(p)) {
            LOGGER.error("BoolenGrid.vSet(): attempt to set pixel beyond boundary!");
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
                if (get(x, y)) {
                    return new Integer2DPoint(x, y);
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
            return rec.realPoint(p, pixelSize);
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
                if (get(x, y)) {
                    sum = sum.add(new Integer2DPoint(x, y));
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
            return rec.realPoint(p, pixelSize);
        }
    }

    /**
     * Generate the entire image from a CSG experession recursively using a quad
     * tree.
     */
    private void generateQuadTree(final Integer2DPoint ipsw, final Integer2DPoint ipne, final CSG2D csgExpression) {
        final Point2D inc = new Point2D(pixelSize / 2, pixelSize / 2);
        final Point2D p0 = rec.realPoint(ipsw, pixelSize);

        // Single pixel?
        if (ipsw.coincidesWith(ipne)) {
            set(ipsw, csgExpression.value(p0) <= 0);
            return;
        }

        // Uniform rectangle?
        final Point2D p1 = rec.realPoint(ipne, pixelSize);
        final Interval i = csgExpression.value(new Rectangle(Point2D.sub(p0, inc), Point2D.add(p1, inc)));
        if (!i.zero()) {
            homogeneous(ipsw, ipne, i.high() <= 0);
            return;
        }

        // Non-uniform, but simple, rectangle
        if (csgExpression.complexity() <= SIMPLE_ENOUGH) {
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
                LOGGER.error("BooleanGrid.generateQuadTree: attempt to divide single pixel!");
            }
            sw = new Integer2DPoint(x0, y0);
            ne = new Integer2DPoint(x0, ym);
            generateQuadTree(
                    sw,
                    ne,
                    csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                            rec.realPoint(ne, pixelSize), inc))));

            sw = new Integer2DPoint(x0, ym + 1);
            ne = new Integer2DPoint(x0, y1);
            generateQuadTree(
                    sw,
                    ne,
                    csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                            rec.realPoint(ne, pixelSize), inc))));

            return;
        }

        // Special case - a single horizontal line of pixels
        if (yd <= 1) {
            sw = new Integer2DPoint(x0, y0);
            ne = new Integer2DPoint(xm, y0);
            generateQuadTree(
                    sw,
                    ne,
                    csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                            rec.realPoint(ne, pixelSize), inc))));

            sw = new Integer2DPoint(xm + 1, y0);
            ne = new Integer2DPoint(x1, y0);
            generateQuadTree(
                    sw,
                    ne,
                    csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                            rec.realPoint(ne, pixelSize), inc))));

            return;
        }

        // General case - 4 quads.
        sw = new Integer2DPoint(x0, y0);
        ne = new Integer2DPoint(xm, ym);
        generateQuadTree(
                sw,
                ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                        rec.realPoint(ne, pixelSize), inc))));

        sw = new Integer2DPoint(x0, ym + 1);
        ne = new Integer2DPoint(xm, y1);
        generateQuadTree(
                sw,
                ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                        rec.realPoint(ne, pixelSize), inc))));

        sw = new Integer2DPoint(xm + 1, ym + 1);
        ne = new Integer2DPoint(x1, y1);
        generateQuadTree(
                sw,
                ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                        rec.realPoint(ne, pixelSize), inc))));

        sw = new Integer2DPoint(xm + 1, y0);
        ne = new Integer2DPoint(x1, ym);
        generateQuadTree(
                sw,
                ne,
                csgExpression.prune(new Rectangle(Point2D.sub(rec.realPoint(sw, pixelSize), inc), Point2D.add(
                        rec.realPoint(ne, pixelSize), inc))));

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
            if (!get(a.add(NEIGHBOUR[i]))) {
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
            if (neighbourCount(i) < 3) {
                bits.set(i, false);
            }
        }
    }

    /**
     * Look-up table to find the index of a neighbour point, n, from the point.
     */
    private static int neighbourIndex(final Integer2DPoint n) {
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
            LOGGER.error("BooleanGrid.neighbourIndex(): not a neighbour point!" + n.toString());
        }
        return 0;
    }

    private int neighbourCount(final int i) {
        int result = 0;
        for (int x = -1; x < 2; x++) {
            for (int y = -1; y < 2; y++) {
                final int j = pixI(x, y);
                if (i + j >= 0 && i + j < bits.size()) {
                    if (bits.get(i + j)) {
                        result++;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Count the solid neighbours of this point
     */
    private int neighbourCount(final Integer2DPoint p) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            if (get(p.add(NEIGHBOUR[i]))) {
                result++;
            }
        }
        return result;
    }

    /**
     * Find the index of the neighbouring point that's closest to a given real
     * direction.
     */
    private static int directionToNeighbour(final Point2D p) {
        double score = Double.NEGATIVE_INFINITY;
        int result = -1;

        for (int i = 0; i < 8; i++) {
            // Can't use neighbour.realPoint as that adds swCorner...
            //  We have to normailze neighbour, to get answers proportional to cosines
            final double s = Point2D.mul(p, new Point2D(NEIGHBOUR[i].x, NEIGHBOUR[i].y).norm());
            if (s > score) {
                result = i;
                score = s;
            }
        }
        if (result < 0) {
            LOGGER.error("BooleanGrid.directionToNeighbour(): scalar product error!" + p.toString());
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
            final Integer2DPoint b = a.add(NEIGHBOUR[i]);
            if (isEdgePixel(b)) {
                if (!vGet(b)) {
                    if (nd < 0) {
                        return b;
                    }
                    final int ns = neighbourCount(b);
                    if (ns <= neighbourScore) {
                        neighbourScore = ns;
                        final int s = NEIGHBOUR_PRODUCT[Math.abs(nd - i)];
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
     * Recursive flood-fill of solid pixels from p to return a BooleanGrid of
     * just the shape connected to that pixel.
     * 
     * @param p
     * @return
     */
    public BooleanGrid floodCopy(final Point2D pp) {
        Integer2DPoint p = rec.convertToInteger2DPoint(pp, pixelSize);
        if (!inside(p) || !this.get(p)) {
            return NOTHING_THERE;
        }
        final BooleanGrid result = new BooleanGrid(pixelSize);
        result.material = material;
        result.visited = null;
        result.rec = new Integer2DRectangle(rec);
        result.bits = new BitSet(result.rec.size.x * result.rec.size.y);

        // We implement our own floodfill stack, rather than using recursion to
        // avoid having to specify a big Java stack just for this one function.

        final int top = 400000;
        final Integer2DPoint[] stack = new Integer2DPoint[top];
        int sp = 0;
        result.set(p, true);
        stack[sp] = p;
        Integer2DPoint q;

        while (sp > -1) {
            p = stack[sp];
            sp--;

            for (int i = 1; i < 8; i = i + 2) {
                q = p.add(NEIGHBOUR[i]);
                if (this.get(q) && !result.get(q)) {
                    result.set(q, true);
                    sp++;
                    if (sp >= top) {
                        LOGGER.error("BooleanGrid.floodCopy(): stack overflow!");
                        return result;
                    }
                    stack[sp] = q;
                }
            }
        }

        return result;
    }

    /**
     * Return all the outlines of all the solid areas as polygons consisting of
     * all the pixels that make up the outlines.
     */
    private Integer2DPolygonList iAllPerimitersRaw() {
        return new BooleanGridWalker(this).marchAll();
    }

    /**
     * Return all the outlines of all the solid areas as polygons in their
     * simplest form.
     */
    private Integer2DPolygonList iAllPerimiters() {
        return iAllPerimitersRaw().simplify();
    }

    /**
     * Return all the outlines of all the solid areas as real-world polygons.
     */
    public PolygonList allPerimiters() {
        PolygonList r = iAllPerimiters().realPolygons(getMaterial(), rec, pixelSize);
        r = r.simplify(1.5 * pixelSize);
        return r;
    }

    /**
     * Generate a sequence of point-pairs where the line h enters and leaves
     * solid areas. The point pairs are stored in a polygon, which should
     * consequently have an even number of points in it on return.
     */
    private Integer2DPolygon hatch(final HalfPlane h) {
        final Integer2DPolygon result = new Integer2DPolygon(false);

        final Interval se = box().wipe(h.pLine(), Interval.bigInterval());

        if (se.empty()) {
            return result;
        }

        final Integer2DPoint s = rec.convertToInteger2DPoint(h.pLine().point(se.low()), pixelSize);
        final Integer2DPoint e = rec.convertToInteger2DPoint(h.pLine().point(se.high()), pixelSize);
        if (get(s)) {
            LOGGER.error("BooleanGrid.hatch(): start point is in solid!");
        }
        final DigitalDifferentialAnalyzer dda = new DigitalDifferentialAnalyzer(s, e);

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
            LOGGER.error("BooleanGrid.hatch(): end point is in solid!");
            result.add(e);
        }

        if (result.size() % 2 != 0) {
            LOGGER.error("BooleanGrid.hatch(): odd number of crossings: " + result.size());
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
        final Integer2DPolygon track = new Integer2DPolygon(false);

        final HalfPlane originPlane = hatches.get(originP);
        final HalfPlane targetPlane = hatches.get(targetP);

        int dir = directionToNeighbour(originPlane.normal());

        if (originPlane.value(targetPlane.pLine().origin()) < 0) {
            dir = neighbourIndex(NEIGHBOUR[dir].neg());
        }

        if (!get(start)) {
            LOGGER.error("BooleanGrid.goToPlane(): start is not solid!");
            return null;
        }

        final double vTarget = targetPlane.value(rec.realPoint(start, pixelSize));

        vSet(start, true);

        Integer2DPoint p = findUnvisitedNeighbourOnEdgeInDirection(start, dir);
        if (p == null) {
            return null;
        }

        Integer2DPoint pNew;
        final double vOrigin = originPlane.value(rec.realPoint(p, pixelSize));
        boolean notCrossedOriginPlane = originPlane.value(rec.realPoint(p, pixelSize)) * vOrigin >= 0;
        boolean notCrossedTargetPlane = targetPlane.value(rec.realPoint(p, pixelSize)) * vTarget >= 0;
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
            notCrossedOriginPlane = originPlane.value(rec.realPoint(p, pixelSize)) * vOrigin >= 0;
            notCrossedTargetPlane = targetPlane.value(rec.realPoint(p, pixelSize)) * vTarget >= 0;
        }

        if (notCrossedOriginPlane) {
            return new SnakeEnd(track, targetP);
        }

        if (notCrossedTargetPlane) {
            return new SnakeEnd(track, originP);
        }

        LOGGER.error("BooleanGrid.goToPlane(): invalid ending!");

        return null;
    }

    /**
     * Find the piece of edge between start and end (if there is one).
     */
    private Integer2DPolygon goToPoint(final Integer2DPoint start, final Integer2DPoint end, final HalfPlane hatch,
            final double tooFar) {
        final Integer2DPolygon track = new Integer2DPolygon(false);

        Integer2DPoint diff = end.sub(start);
        if (diff.x == 0 && diff.y == 0) {
            track.add(start);
            return track;
        }

        int dir = directionToNeighbour(new Point2D(diff.x, diff.y));

        if (!get(start)) {
            LOGGER.error("BooleanGrid.goToPlane(): start is not solid!");
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
                lost = Math.abs(hatch.value(rec.realPoint(p, pixelSize))) > tooFar;
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
    private Integer2DPolygon snakeGrow(final Integer2DPolygonList ipl, final List<HalfPlane> hatches, int thisHatch, int thisPt) {
        final Integer2DPolygon result = new Integer2DPolygon(false);

        Integer2DPolygon thisPolygon = ipl.polygon(thisHatch);
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
        final Point2D rp = rec.realPoint(p, pixelSize);
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
    private void joinUpSnakes(final Integer2DPolygonList snakes, final List<HalfPlane> hatches, final double gap) {
        int i = 0;
        if (hatches.size() <= 0) {
            return;
        }
        final Point2D n = hatches.get(0).normal();
        Integer2DPolygon track;
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

                Point2D diff = Point2D.sub(rec.realPoint(jStart, pixelSize), rec.realPoint(iStart, pixelSize));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iStart, jStart, hPlane(iStart, hatches), gap);
                    if (track != null) {
                        final Integer2DPolygon p = snakes.polygon(i).negate();
                        p.add(track);
                        p.add(snakes.polygon(j));
                        snakes.set(i, p);
                        snakes.remove(j);
                        incrementI = false;
                        break;
                    }
                }

                diff = Point2D.sub(rec.realPoint(jEnd, pixelSize), rec.realPoint(iStart, pixelSize));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iStart, jEnd, hPlane(iStart, hatches), gap);
                    if (track != null) {
                        final Integer2DPolygon p = snakes.polygon(j);
                        p.add(track.negate());
                        p.add(snakes.polygon(i));
                        snakes.set(i, p);
                        snakes.remove(j);
                        incrementI = false;
                        break;
                    }
                }

                diff = Point2D.sub(rec.realPoint(jStart, pixelSize), rec.realPoint(iEnd, pixelSize));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iEnd, jStart, hPlane(iEnd, hatches), gap);
                    if (track != null) {
                        final Integer2DPolygon p = snakes.polygon(i);
                        p.add(track);
                        p.add(snakes.polygon(j));
                        snakes.set(i, p);
                        snakes.remove(j);
                        incrementI = false;
                        break;
                    }
                }

                diff = Point2D.sub(rec.realPoint(jEnd, pixelSize), rec.realPoint(iEnd, pixelSize));
                d = Point2D.mul(diff, n);
                if (Math.abs(d) < 1.5 * gap) {
                    track = goToPoint(iEnd, jEnd, hPlane(iEnd, hatches), gap);
                    if (track != null) {
                        final Integer2DPolygon p = snakes.polygon(i);
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
     * @param pathOptimize
     *            TODO
     * 
     * @return a polygon list of hatch lines
     */
    public PolygonList hatch(final HalfPlane hp, final double gap, final boolean pathOptimize) {
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
        final Integer2DPolygonList iHatches = new Integer2DPolygonList();

        double g = 0;
        while (g < d) {
            final Integer2DPolygon ip = hatch(hatcher);

            if (ip.size() > 0) {
                hatches.add(hatcher);
                iHatches.add(ip);
            }
            hatcher = hatcher.offset(gap);
            g += gap;
        }

        // Now we have the individual hatch lines, join them up
        final Integer2DPolygonList snakes = new Integer2DPolygonList();
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

        if (pathOptimize) {
            joinUpSnakes(snakes, hatches, gap);
        }
        resetVisited();
        return snakes.realPolygons(getMaterial(), rec, pixelSize).simplify(1.5 * pixelSize);
    }

    /**
     * Offset the pattern by a given real-world distance. If the distance is
     * negative the pattern is shrunk; if it is positive it is grown;
     */
    public BooleanGrid createOffsetGrid(final double dist) {
        final int r = (int) Math.round(dist / pixelSize);

        final BooleanGrid result = new BooleanGrid(this, pixelSize);
        if (r == 0) {
            return result;
        }
        result.offset(dist);
        if (result.isEmpty()) {
            return NOTHING_THERE;
        }
        return result;
    }

    /**
     * Offset the pattern by a given real-world distance. If the distance is
     * negative the pattern is shrunk; if it is positive it is grown;
     */
    public void offset(final double dist) {
        final int r = (int) Math.round(dist / pixelSize);
        if (r == 0) {
            return;
        }

        if (r > 0) { // just got bigger, need more room
            final Integer2DRectangle oldRectangle = rec;
            rec = oldRectangle.createOffsetRectangle(r);
            final BitSet oldBits = bits;
            bits = new BitSet(rec.size.x * rec.size.y);
            final int offxOut = oldRectangle.swCorner.x - rec.swCorner.x;
            final int offyOut = oldRectangle.swCorner.y - rec.swCorner.y;
            for (int x = 0; x < oldRectangle.size.x; x++) {
                for (int y = 0; y < oldRectangle.size.y; y++) {
                    bits.set(pixI(x + offxOut, y + offyOut), oldBits.get(x * oldRectangle.size.y + y));
                }
            }
        }

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
        deWhisker();
    }

    Integer2DRectangle getRec() {
        return rec;
    }

    /**
     * Compute the union of two bit patterns, forcing material on the result.
     */
    public static BooleanGrid union(final BooleanGrid d, final BooleanGrid e, final String resultMaterial) {
        BooleanGrid result;

        if (d == NOTHING_THERE) {
            if (e == NOTHING_THERE) {
                return NOTHING_THERE;
            }
            if (e.material == resultMaterial) { // TODO: string comparison by identity
                return e;
            }
            result = new BooleanGrid(e, e.pixelSize);
            result.material = resultMaterial;
            return result;
        }

        if (e == NOTHING_THERE) {
            if (d.material == resultMaterial) {
                return d;
            }
            result = new BooleanGrid(d, e.pixelSize);
            result.material = resultMaterial;
            return result;
        }

        if (d.rec.coincidesWith(e.rec)) {
            result = new BooleanGrid(d, e.pixelSize);
            result.bits.or(e.bits);
        } else {
            final Integer2DRectangle u = d.rec.union(e.rec);
            result = new BooleanGrid(d, u, d.pixelSize);
            final BooleanGrid temp = new BooleanGrid(e, u, e.pixelSize);
            result.bits.or(temp.bits);
        }
        result.material = resultMaterial;
        return result;
    }

    /**
     * Compute the union of two bit patterns
     */
    static BooleanGrid union(final BooleanGrid d, final BooleanGrid e) {
        final BooleanGrid result = union(d, e, d.material);
        if (result != NOTHING_THERE && d.material != e.material) {
            LOGGER.error("BooleanGrid.union(): attempt to union two bitmaps of different materials: " + d.getMaterial()
                    + " and " + e.getMaterial());
        }
        return result;
    }

    /**
     * Compute the intersection of two bit patterns
     */
    private static BooleanGrid intersection(final BooleanGrid d, final BooleanGrid e, final String resultMaterial) {
        if (d == NOTHING_THERE || e == NOTHING_THERE) {
            return NOTHING_THERE;
        }

        BooleanGrid result;
        if (d.rec.coincidesWith(e.rec)) {
            result = new BooleanGrid(d, e.pixelSize);
            result.bits.and(e.bits);
        } else {
            final Integer2DRectangle u = d.rec.intersection(e.rec);
            if (u.isEmpty()) {
                return NOTHING_THERE;
            }
            result = new BooleanGrid(d, u, d.pixelSize);
            final BooleanGrid temp = new BooleanGrid(e, u, d.pixelSize);
            result.bits.and(temp.bits);
        }
        if (result.isEmpty()) {
            return NOTHING_THERE;
        }
        result.deWhisker();
        result.material = resultMaterial;
        return result;
    }

    /**
     * Compute the intersection of two bit patterns
     */
    public static BooleanGrid intersection(final BooleanGrid d, final BooleanGrid e) {
        final BooleanGrid result = intersection(d, e, d.material);
        if (result != NOTHING_THERE && d.material != e.material) {
            LOGGER.error("BooleanGrid.intersection(): attempt to intersect two bitmaps of different materials: "
                    + d.getMaterial() + " and " + e.getMaterial());
        }
        return result;
    }

    /**
     * Grid d - grid e, forcing attribute a on the result d's rectangle is
     * presumed to contain the result. TODO: write a function to compute the
     * rectangle from the bitmap
     */
    public static BooleanGrid difference(final BooleanGrid d, final BooleanGrid e, final String resultMaterial) {
        if (d == NOTHING_THERE) {
            return NOTHING_THERE;
        }

        BooleanGrid result;

        if (e == NOTHING_THERE) {
            if (d.material == resultMaterial) {
                return d;
            }
            result = new BooleanGrid(d, e.pixelSize);
            result.material = resultMaterial;
            return result;
        }

        result = new BooleanGrid(d, e.pixelSize);
        BooleanGrid temp;
        if (d.rec.coincidesWith(e.rec)) {
            temp = e;
        } else {
            temp = new BooleanGrid(e, result.rec, e.pixelSize);
        }
        result.bits.andNot(temp.bits);
        if (result.isEmpty()) {
            return NOTHING_THERE;
        }
        result.deWhisker();
        result.material = resultMaterial;
        return result;
    }

    /**
     * Grid d - grid e d's rectangle is presumed to contain the result. TODO:
     * write a function to compute the rectangle from the bitmap
     */
    public static BooleanGrid difference(final BooleanGrid d, final BooleanGrid e) {
        final BooleanGrid result = difference(d, e, d.material);
        if (result != NOTHING_THERE && d.material != e.material) {
            LOGGER.error("BooleanGrid.difference(): attempt to subtract two bitmaps of different materials: " + d.getMaterial()
                    + " and " + e.getMaterial());
        }
        return result;
    }

    public BooleanGrid subtractPolygons(final PolygonList polygons, final double width) {
        final BooleanGrid result = new BooleanGrid(this, pixelSize);
        for (int i = 0; i < polygons.size(); i++) {
            final Polygon polygon = polygons.polygon(i);
            result.subtract(polygon, width);
        }
        return result;
    }

    private void subtract(final Polygon polygon, final double width) {
        final int pixelWidth = (int) Math.round(width / pixelSize);
        for (int i = 0; i < polygon.size(); i++) {
            final Integer2DPoint start = rec.convertToInteger2DPoint(polygon.point(i), pixelSize);
            final Integer2DPoint end = rec.convertToInteger2DPoint(polygon.point((i + 1) % polygon.size()), pixelSize);
            rectangle(start, end, pixelWidth, false);
            rectangle(end.add(new Integer2DPoint(-pixelWidth, 0)), end.add(new Integer2DPoint(pixelWidth, 0)), pixelWidth,
                    false);
        }
    }
}
