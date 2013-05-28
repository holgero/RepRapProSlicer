/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *   originally extracted from BooleanGrid 
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
package org.reprap.geometry.grids;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.Interval;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;

public class Hatcher {
    private static final Logger LOGGER = LogManager.getLogger(Hatcher.class);
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

    private final BooleanGrid grid;
    private final double pixelSize;
    private final Integer2DRectangle rectangle;
    private final String material;
    private final BitSet visited;

    public Hatcher(final BooleanGrid grid) {
        this.grid = grid;
        pixelSize = grid.getPixelSize();
        rectangle = grid.getRectangle();
        material = grid.getMaterial();
        visited = new BitSet(rectangle.getSizeX() * rectangle.getSizeY());
    }

    public PolygonList hatch(final HalfPlane hp, final double gap, final boolean pathOptimize) {
        if (gap <= 0) {
            return new PolygonList();
        }

        final Rectangle big = rectangle.realRectangle(pixelSize).scale(1.1);
        final List<HalfPlane> hatches = new ArrayList<HalfPlane>();
        final Integer2DPolygonList iHatches = new Integer2DPolygonList();
        collectHatches(hp.offset(calculateDistance(hp, gap, big)), gap, Math.sqrt(big.dSquared()), hatches, iHatches);

        final Integer2DPolygonList snakes = createSnakes(hatches, iHatches);
        if (pathOptimize) {
            joinUpSnakes(snakes, hatches, gap);
        }
        return snakes.realPolygons(material, rectangle, pixelSize).simplify(1.5 * pixelSize);
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

        if (!grid.get(start)) {
            LOGGER.error("start is not solid!");
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

        LOGGER.error("invalid ending!");

        return null;
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
            //  We have to normalize neighbour, to get answers proportional to cosines
            final double s = Point2D.mul(p, new Point2D(NEIGHBOUR[i].getX(), NEIGHBOUR[i].getY()).norm());
            if (s > score) {
                result = i;
                score = s;
            }
        }
        if (result < 0) {
            LOGGER.error("scalar product error!" + p.toString());
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
     * Is a pixel on an edge? If it is solid and there is air at at least one of
     * north, south, east, or west, then yes; otherwise no.
     */
    private boolean isEdgePixel(final Integer2DPoint a) {
        if (!grid.get(a)) {
            return false;
        }

        for (int i = 1; i < 8; i += 2) {
            if (!grid.get(a.add(NEIGHBOUR[i]))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Count the solid neighbours of this point
     */
    private int neighbourCount(final Integer2DPoint p) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            if (grid.get(p.add(NEIGHBOUR[i]))) {
                result++;
            }
        }
        return result;
    }

    /**
     * Has this point been visited?
     */
    private boolean vGet(final Integer2DPoint p) {
        if (!grid.inside(p)) {
            return false;
        }
        return visited.get(grid.pixI(p));
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
            LOGGER.error("not a neighbour point!" + n.toString());
        }
        return 0;
    }

    /**
     * Set a point as visited
     */
    private void vSet(final Integer2DPoint p, final boolean v) {
        if (!grid.inside(p)) {
            LOGGER.error("attempt to set pixel beyond boundary!");
            return;
        }
        visited.set(grid.pixI(p), v);
    }

    private static double calculateDistance(final HalfPlane hp, final double gap, final Rectangle big) {
        final Point2D orth = hp.normal();
        final Point2D org = determineHatchOrigin(big, orth);
        double dist = Point2D.mul(org, orth) / gap;
        dist = (1 + (long) dist) * gap;
        return dist;
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

    /**
     * Generate a sequence of point-pairs where the line h enters and leaves
     * solid areas. The point pairs are stored in a polygon, which should
     * consequently have an even number of points in it on return.
     */
    private Integer2DPolygon hatch(final HalfPlane h) {
        final Integer2DPolygon result = new Integer2DPolygon(false);

        final Interval se = rectangle.realRectangle(pixelSize).wipe(h.pLine(), Interval.bigInterval());

        if (se.empty()) {
            return result;
        }

        final Integer2DPoint s = rectangle.convertToInteger2DPoint(h.pLine().point(se.low()), pixelSize);
        final Integer2DPoint e = rectangle.convertToInteger2DPoint(h.pLine().point(se.high()), pixelSize);
        if (grid.get(s)) {
            LOGGER.error("start point is in solid!");
        }
        final DigitalDifferentialAnalyzer dda = new DigitalDifferentialAnalyzer(s, e);

        Integer2DPoint n = dda.next();
        Integer2DPoint nOld = n;
        boolean v;
        boolean vs = false;
        while (n != null) {
            v = grid.get(n);
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

        if (grid.get(e)) {
            LOGGER.error("end point is in solid!");
            result.add(e);
        }

        if (result.size() % 2 != 0) {
            LOGGER.error("odd number of crossings: " + result.size());
        }
        return result;
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

        if (!grid.get(start)) {
            LOGGER.error("start is not solid!");
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
}
