/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *  separated grid walking from BooleanGrid
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
 */package org.reprap.geometry.polygons;

import java.util.BitSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BooleanGridWalker {
    private static final Logger LOGGER = LogManager.getLogger(BooleanGridWalker.class);

    /**
     * Run round the eight neighbours of a pixel anti clockwise from bottom left
     * 
     * <pre>
     *    6    5    4
     *      NW N NE 
     *    7 W     E 3
     *      SW S SE
     *    0    1    2
     * </pre>
     */
    enum Neighbour {
        SW(-1, -1), S(0, -1), SE(1, -1), E(1, 0), NE(1, 1), N(0, 1), NW(-1, 1), W(-1, 0);
        private final int dx;
        private final int dy;

        Neighbour(final int dx, final int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        Integer2DPoint from(final Integer2DPoint from) {
            return new Integer2DPoint(fromX(from.x), fromY(from.y));
        }

        int fromX(final int x) {
            return x + dx;
        }

        int fromY(final int y) {
            return y + dy;
        }
    }

    // Marching squares directions.  2x2 grid bits:
    //
    //    0  1
    //
    //    2  3
    private static final Neighbour[] MARCHDIRECTIONS = { null, // 0
            Neighbour.N, // 1
            Neighbour.E, // 2
            Neighbour.E, // 3
            Neighbour.W, // 4
            Neighbour.N, // 5
            Neighbour.W, // 6
            Neighbour.E, // 7
            Neighbour.S, // 8
            Neighbour.N, // 9
            Neighbour.S, // 10
            Neighbour.S, // 11
            Neighbour.W, // 12
            Neighbour.N, // 13
            Neighbour.W, // 14
            null // 15
    };

    private final BooleanGrid grid;
    private final Integer2DPoint size;
    private final BitSet visited;

    BooleanGridWalker(final BooleanGrid booleanGrid) {
        grid = booleanGrid;
        size = grid.getRec().size;
        visited = new BitSet(size.x * size.y);
    }

    /**
     * Run marching squares round all polygons in the pattern, returning a list
     * of them all
     */
    Integer2DPolygonList marchAll() {
        final Integer2DPolygonList result = new Integer2DPolygonList();
        if (grid.isEmpty()) {
            return result;
        }

        for (int x = 0; x < size.x - 1; x++) {
            for (int y = 0; y < size.y - 1; y++) {
                final int m = marchPattern(x, y);
                if (m != 0 && m != 15) {
                    final Integer2DPoint start = new Integer2DPoint(x, y);
                    if (isValidStartpointForMarch(start)) {
                        final Integer2DPolygon p = marchRound(start);
                        if (p.size() > 2) {
                            result.add(p);
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean isValidStartpointForMarch(final Integer2DPoint start) {
        return !isVisited(start) && !isVisited(Neighbour.S.from(start)) && !isVisited(Neighbour.SE.from(start))
                && !isVisited(Neighbour.E.from(start));
    }

    /**
     * Run marching squares round the polygon starting with the 2x2 march
     * pattern at start
     */
    private Integer2DPolygon marchRound(final Integer2DPoint start) {
        final Integer2DPolygon result = new Integer2DPolygon(true);
        Integer2DPoint here = new Integer2DPoint(start);
        Integer2DPoint last = null;

        do {
            final int m = marchPattern(here.x, here.y);
            switch (m) {
            case 1:
                addToResult(result, here);
                break;
            case 2:
                addToResult(result, Neighbour.E.from(here));
                break;
            case 3:
                addToResult(result, here);
                addToResult(result, Neighbour.E.from(here));
                break;
            case 4:
                addToResult(result, Neighbour.S.from(here));
                break;
            case 5:
                addToResult(result, here);
                addToResult(result, Neighbour.S.from(here));
                break;
            case 6:
                if (last == null) {
                    LOGGER.error("dud 2x2 grid: " + m + " at " + here.toString() + "\n" + printNearby(here, 4));
                    return result;
                }
                setVisited(here, false);
                delete(Neighbour.E.from(here));
                here = last;
                last = null;
                LOGGER.debug("changed grid (m=" + m + ") and backtracked to " + here.toString() + "\n" + printNearby(here, 4));
                continue;
            case 7:
                addToResult(result, Neighbour.S.from(here));
                addToResult(result, Neighbour.E.from(here));
                break;
            case 8:
                addToResult(result, Neighbour.SE.from(here));
                break;
            case 9:
                if (last == null) {
                    LOGGER.error("dud 2x2 grid: " + m + " at " + here.toString() + "\n" + printNearby(here, 4));
                    return result;
                }
                setVisited(here, false);
                delete(Neighbour.SE.from(here));
                here = last;
                last = null;
                LOGGER.debug("changed grid (m=" + m + ") and backtracked to " + here.toString() + "\n" + printNearby(here, 4));
                continue;
            case 10:
                addToResult(result, Neighbour.E.from(here));
                addToResult(result, Neighbour.SE.from(here));
                break;
            case 11:
                addToResult(result, here);
                addToResult(result, Neighbour.SE.from(here));
                break;
            case 12:
                addToResult(result, Neighbour.SE.from(here));
                addToResult(result, Neighbour.S.from(here));
                break;
            case 13:
                addToResult(result, Neighbour.SE.from(here));
                addToResult(result, here);
                break;
            case 14:
                addToResult(result, Neighbour.E.from(here));
                addToResult(result, Neighbour.S.from(here));
                break;

            default:
                LOGGER.error("dud 2x2 grid: " + m + " at " + here.toString() + "\n" + printNearby(here, 4));
                return result;
            }
            last = here;
            here = MARCHDIRECTIONS[m].from(here);
        } while (!here.coincidesWith(start));

        return result;
    }

    private void delete(final Integer2DPoint pix) {
        grid.set(pix, false);
        setVisited(pix, false);
    }

    private void addToResult(final Integer2DPolygon result, final Integer2DPoint pix) {
        if (!isVisited(pix)) {
            result.add(pix);
            setVisited(pix, true);
        }
    }

    /**
     * Calculate the 4-bit marching squares value for a point
     */
    private int marchPattern(final int x, final int y) {
        int result = 0;

        if (grid.get(x, y)) {
            result |= 1;
        }
        if (grid.get(Neighbour.E.fromX(x), Neighbour.E.fromY(y))) {
            result |= 2;
        }
        if (grid.get(Neighbour.S.fromX(x), Neighbour.S.fromY(y))) {
            result |= 4;
        }
        if (grid.get(Neighbour.SE.fromX(x), Neighbour.SE.fromY(y))) {
            result |= 8;
        }
        return result;
    }

    /**
     * Set a point as visited
     */
    private void setVisited(final Integer2DPoint p, final boolean v) {
        if (!isInside(p)) {
            throw new RuntimeException("attempt to set pixel beyond boundaries (" + size + "): " + p);
        }
        visited.set(pixelIndex(p), v);
    }

    /**
     * Has this point been visited?
     */
    private boolean isVisited(final Integer2DPoint p) {
        if (!isInside(p)) {
            throw new RuntimeException("attempt to get pixel beyond boundaries (" + size + "): " + p);
        }
        return visited.get(pixelIndex(p));
    }

    /**
     * The index of a pixel in the 1D bit array.
     */
    private int pixelIndex(final int x, final int y) {
        return x * size.y + y;
    }

    /**
     * The index of a pixel in the 1D bit array.
     */
    private int pixelIndex(final Integer2DPoint p) {
        return pixelIndex(p.x, p.y);
    }

    /**
     * Is a point inside the image?
     */
    private boolean isInside(final Integer2DPoint p) {
        if (p.x < 0 || p.x >= size.x) {
            return false;
        }
        if (p.y < 0 || p.y >= size.y) {
            return false;
        }
        return true;
    }

    /**
     * Useful debugging function
     */
    private String printNearby(final Integer2DPoint point, final int delta) {
        final StringBuilder output = new StringBuilder();
        for (int y = Math.min(point.y + delta, size.y - 1); y >= Math.max(0, point.y - delta); y--) {
            for (int x = Math.max(0, point.x - delta); x <= Math.min(point.x + delta, size.x - 1); x++) {
                output.append(" ");
                final Integer2DPoint printPoint = new Integer2DPoint(x, y);
                if (printPoint.coincidesWith(point)) {
                    if (grid.get(point)) {
                        output.append("+");
                    } else {
                        output.append("o");
                    }
                } else if (grid.get(printPoint)) {
                    if (isVisited(printPoint)) {
                        output.append("V");
                    } else {
                        output.append("*");
                    }
                } else {
                    if (isVisited(printPoint)) {
                        output.append("v");
                    } else {
                        output.append(".");
                    }
                }
            }
            output.append("\n");
        }
        return output.toString();
    }
}
