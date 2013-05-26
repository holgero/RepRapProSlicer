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

    /**
     * How simple does a CSG expression have to be to not be worth pruning
     * further?
     */
    public static final BooleanGrid NOTHING_THERE = new BooleanGrid(0.0, null, new Integer2DRectangle(), new BitSet(1));

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

    private final double pixelSize;

    /**
     * The pixel map
     */
    private final BitSet bits;

    /**
     * Flags for visited pixels during searches
     */
    private BitSet visited;

    /**
     * The rectangle the pixelmap covers
     */
    private final Integer2DRectangle rectangle;
    private String material;

    private BooleanGrid(final double pixelSize, final String material, final Integer2DRectangle rectangle, final BitSet bits) {
        this.pixelSize = pixelSize;
        this.material = material;
        this.rectangle = rectangle;
        this.bits = bits;
        visited = null;
    }

    private BooleanGrid(final double pixelSize, final String material, final Integer2DRectangle rectangle) {
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
        generateQuadTree(new Integer2DPoint(0, 0), new Integer2DPoint(rectangle.getSizeX() - 1, rectangle.getSizeY() - 1),
                csgExp);
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
    private int pixI(final Integer2DPoint p) {
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
    private boolean inside(final Integer2DPoint p) {
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
     * Set a whole rectangle to one value
     */
    private void homogeneous(final Integer2DPoint ipsw, final Integer2DPoint ipne, final boolean v) {
        for (int x = ipsw.getX(); x <= ipne.getX(); x++) {
            bits.set(pixI(x, ipsw.getY()), pixI(x, ipne.getY()) + 1, v);
        }
    }

    /**
     * The rectangle surrounding the set pixels in real coordinates.
     * 
     * TODO: check if this is obsoleted by
     * {@link Integer2DRectangle#realRectangle(double)}
     */
    private Rectangle box() {
        final Integer2DPoint r = new Integer2DPoint(0, 0);
        final Integer2DPoint r1 = new Integer2DPoint(rectangle.getSizeX() - 1, rectangle.getSizeY() - 1);
        return new Rectangle(rectangle.realPoint(r, pixelSize), rectangle.realPoint(r1, pixelSize));
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
     * Set a point as visited
     */
    private void vSet(final Integer2DPoint p, final boolean v) {
        if (!inside(p)) {
            LOGGER.error("BoolenGrid.vSet(): attempt to set pixel beyond boundary!");
            return;
        }
        if (visited == null) {
            visited = new BitSet(rectangle.getSizeX() * rectangle.getSizeY());
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
        for (int x = 0; x < rectangle.getSizeX(); x++) {
            for (int y = 0; y < rectangle.getSizeY(); y++) {
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
            return rectangle.realPoint(p, pixelSize);
        }
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
     * Generate the entire image from a CSG expression recursively using a quad
     * tree.
     */
    private void generateQuadTree(final Integer2DPoint ipsw, final Integer2DPoint ipne, final CSG2D csgExpression) {
        final Point2D p0 = rectangle.realPoint(ipsw, pixelSize);

        // Single pixel?
        if (ipsw.coincidesWith(ipne)) {
            set(ipsw, csgExpression.value(p0) <= 0);
            return;
        }

        final Point2D inc = new Point2D(pixelSize / 2, pixelSize / 2);
        // Uniform rectangle?
        final Point2D p1 = rectangle.realPoint(ipne, pixelSize);
        final Interval i = csgExpression.value(new Rectangle(Point2D.sub(p0, inc), Point2D.add(p1, inc)));
        if (!i.zero()) {
            homogeneous(ipsw, ipne, i.high() <= 0);
            return;
        }

        // Divide this rectangle into four (roughly) congruent quads.
        // Work out the corner coordinates.
        final int x0 = ipsw.getX();
        final int y0 = ipsw.getY();
        final int x1 = ipne.getX();
        final int y1 = ipne.getY();
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

        // Special case - a single vertical line of pixels
        if (xd <= 1) {
            if (yd <= 1) {
                LOGGER.error("BooleanGrid.generateQuadTree: attempt to divide single pixel!");
            }
            callGenerateQuadTree(x0, y0, x0, ym, inc, csgExpression);
            callGenerateQuadTree(x0, ym + 1, x0, y1, inc, csgExpression);
            return;
        }

        // Special case - a single horizontal line of pixels
        if (yd <= 1) {
            callGenerateQuadTree(x0, y0, xm, y0, inc, csgExpression);
            callGenerateQuadTree(xm + 1, y0, x1, y0, inc, csgExpression);
            return;
        }

        // General case - 4 quads.
        callGenerateQuadTree(x0, y0, xm, ym, inc, csgExpression);
        callGenerateQuadTree(x0, ym + 1, xm, y1, inc, csgExpression);
        callGenerateQuadTree(xm + 1, ym + 1, x1, y1, inc, csgExpression);
        callGenerateQuadTree(xm + 1, y0, x1, ym, inc, csgExpression);
    }

    private void callGenerateQuadTree(final int swX, final int swY, final int neX, final int neY, final Point2D inc,
            final CSG2D csgExpression) {
        final Integer2DPoint sw = new Integer2DPoint(swX, swY);
        final Integer2DPoint ne = new Integer2DPoint(neX, neY);
        final Rectangle realRectangle = new Rectangle(Point2D.sub(rectangle.realPoint(sw, pixelSize), inc), Point2D.add(
                rectangle.realPoint(ne, pixelSize), inc));
        generateQuadTree(sw, ne, csgExpression.prune(realRectangle));
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
     * Look-up table to find the index of a neighbour point, n, from the point.
     */
    private static int neighbourIndex(final Integer2DPoint n) {
        switch ((n.getY() + 1) * 3 + n.getX() + 1) {
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
            final double s = Point2D.mul(p, new Point2D(NEIGHBOUR[i].getX(), NEIGHBOUR[i].getY()).norm());
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
        Integer2DPoint p = rectangle.convertToInteger2DPoint(pp, pixelSize);
        if (!inside(p) || !this.get(p)) {
            return NOTHING_THERE;
        }
        final BooleanGrid result = new BooleanGrid(pixelSize, material, new Integer2DRectangle(rectangle));

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

        final Integer2DPoint s = rectangle.convertToInteger2DPoint(h.pLine().point(se.low()), pixelSize);
        final Integer2DPoint e = rectangle.convertToInteger2DPoint(h.pLine().point(se.high()), pixelSize);
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

        final double vTarget = targetPlane.value(rectangle.realPoint(start, pixelSize));

        vSet(start, true);

        Integer2DPoint p = findUnvisitedNeighbourOnEdgeInDirection(start, dir);
        if (p == null) {
            return null;
        }

        Integer2DPoint pNew;
        final double vOrigin = originPlane.value(rectangle.realPoint(p, pixelSize));
        boolean notCrossedOriginPlane = originPlane.value(rectangle.realPoint(p, pixelSize)) * vOrigin >= 0;
        boolean notCrossedTargetPlane = targetPlane.value(rectangle.realPoint(p, pixelSize)) * vTarget >= 0;
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
            notCrossedOriginPlane = originPlane.value(rectangle.realPoint(p, pixelSize)) * vOrigin >= 0;
            notCrossedTargetPlane = targetPlane.value(rectangle.realPoint(p, pixelSize)) * vTarget >= 0;
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
        if (diff.getX() == 0 && diff.getY() == 0) {
            track.add(start);
            return track;
        }

        int dir = directionToNeighbour(new Point2D(diff.getX(), diff.getY()));

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
                lost = Math.abs(hatch.value(rectangle.realPoint(p, pixelSize))) > tooFar;
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
            dir = directionToNeighbour(new Point2D(diff.getX(), diff.getY()));
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
        final Point2D rp = rectangle.realPoint(p, pixelSize);
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

                Point2D diff = Point2D.sub(rectangle.realPoint(jStart, pixelSize), rectangle.realPoint(iStart, pixelSize));
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

                diff = Point2D.sub(rectangle.realPoint(jEnd, pixelSize), rectangle.realPoint(iStart, pixelSize));
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

                diff = Point2D.sub(rectangle.realPoint(jStart, pixelSize), rectangle.realPoint(iEnd, pixelSize));
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

                diff = Point2D.sub(rectangle.realPoint(jEnd, pixelSize), rectangle.realPoint(iEnd, pixelSize));
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
     * @return a polygon list of hatch lines
     */
    public PolygonList hatch(final HalfPlane hp, final double gap, final boolean pathOptimize) {
        if (gap <= 0) {
            return new PolygonList();
        }

        final Rectangle big = box().scale(1.1);
        final List<HalfPlane> hatches = new ArrayList<HalfPlane>();
        final Integer2DPolygonList iHatches = new Integer2DPolygonList();
        collectHatches(hp.offset(calculateDistance(hp, gap, big)), gap, Math.sqrt(big.dSquared()), hatches, iHatches);

        final Integer2DPolygonList snakes = createSnakes(hatches, iHatches);
        if (pathOptimize) {
            joinUpSnakes(snakes, hatches, gap);
        }
        resetVisited();
        return snakes.realPolygons(getMaterial(), rectangle, pixelSize).simplify(1.5 * pixelSize);
    }

    private Integer2DPolygonList createSnakes(final List<HalfPlane> hatches, final Integer2DPolygonList iHatches) {
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
        return snakes;
    }

    private static double calculateDistance(final HalfPlane hp, final double gap, final Rectangle big) {
        final Point2D orth = hp.normal();
        final Point2D org = determineHatchOrigin(big, orth);
        double dist = Point2D.mul(org, orth) / gap;
        dist = (1 + (long) dist) * gap;
        return dist;
    }

    private void collectHatches(HalfPlane hatcher, final double gap, final double maxLength, final List<HalfPlane> hatches,
            final Integer2DPolygonList iHatches) {
        double g = 0;
        while (g < maxLength) {
            final Integer2DPolygon ip = hatch(hatcher);
            if (ip.size() > 0) {
                hatches.add(hatcher);
                iHatches.add(ip);
            }
            hatcher = hatcher.offset(gap);
            g += gap;
        }
    }

    private static Point2D determineHatchOrigin(final Rectangle big, final Point2D orth) {
        final int quadPointing = (int) (2 + 2 * Math.atan2(orth.y(), orth.x()) / Math.PI);
        switch (quadPointing) {
        case 1:
            return big.nw();
        case 2:
            return big.sw();
        case 3:
            return big.se();
        case 0:
        default:
            return big.ne();
        }
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

    Integer2DRectangle getRec() {
        return rectangle;
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
