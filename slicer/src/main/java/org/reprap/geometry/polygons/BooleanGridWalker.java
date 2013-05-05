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

    private final class DebugPrinter {
        @Override
        public String toString() {
            return printNearby(here, 4);
        }

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
                    } else if (printPoint.coincidesWith(start)) {
                        if (grid.get(start)) {
                            output.append("S");
                        } else {
                            output.append("s");
                        }
                    } else if (last != null && printPoint.coincidesWith(last)) {
                        if (grid.get(last)) {
                            output.append("L");
                        } else {
                            output.append("l");
                        }
                    } else if (grid.get(printPoint)) {
                        if (isVisited(printPoint.x, printPoint.y)) {
                            output.append("V");
                        } else {
                            output.append("*");
                        }
                    } else {
                        if (isVisited(printPoint.x, printPoint.y)) {
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

    private final DebugPrinter debugPrinter = new DebugPrinter();

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
    private Integer2DPoint start;
    private Integer2DPoint here;
    private Integer2DPoint last;

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
                    if (canMarch(x, y)) {
                        start = new Integer2DPoint(x, y);
                        final Integer2DPolygon p = marchRound();
                        if (p.size() > 2) {
                            result.add(p);
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean canMarch(final int x, final int y) {
        return !isVisited(x, y) && !isVisited(Neighbour.S.fromX(x), Neighbour.S.fromY(y))
                && !isVisited(Neighbour.SE.fromX(x), Neighbour.SE.fromY(y))
                && !isVisited(Neighbour.E.fromX(x), Neighbour.E.fromY(y));
    }

    /**
     * Run marching squares round the polygon starting with the 2x2 march
     * pattern at start
     */
    private Integer2DPolygon marchRound() {
        final Integer2DPolygon result = new Integer2DPolygon(true);
        here = new Integer2DPoint(start);
        last = null;

        do {
            final int m = marchPattern(here.x, here.y);
            LOGGER.trace("walking ({})\n{}", here, debugPrinter);
            switch (m) {
            case 1:
                addToResult(result, here);
                break;
            case 2:
                addToResult(result, Neighbour.E.fromX(here.x), Neighbour.E.fromY(here.y));
                break;
            case 3:
                addToResult(result, here);
                addToResult(result, Neighbour.E.fromX(here.x), Neighbour.E.fromY(here.y));
                break;
            case 4:
                addToResult(result, Neighbour.S.fromX(here.x), Neighbour.S.fromY(here.y));
                break;
            case 5:
                addToResult(result, here);
                addToResult(result, Neighbour.S.fromX(here.x), Neighbour.S.fromY(here.y));
                break;
            case 6:
                if (last == null) {
                    LOGGER.error("dud 2x2 grid: {} at {}\n{}", m, here, debugPrinter);
                    return result;
                }
                LOGGER.debug("dud 2x2 grid: {} at {}\n{}", m, here, debugPrinter);
                setVisited(here.x, here.y, false);
                if (last.x + last.y > here.x + here.y) {
                    delete(Neighbour.S.from(here));
                } else {
                    delete(Neighbour.E.from(here));
                }
                here = last;
                last = null;
                LOGGER.debug("changed grid (m={}), continue at {}\n{}", m, here, debugPrinter);
                continue;
            case 7:
                addToResult(result, Neighbour.S.fromX(here.x), Neighbour.S.fromY(here.y));
                addToResult(result, Neighbour.E.fromX(here.x), Neighbour.E.fromY(here.y));
                break;
            case 8:
                addToResult(result, Neighbour.SE.fromX(here.x), Neighbour.SE.fromY(here.y));
                break;
            case 9:
                if (last == null) {
                    LOGGER.error("dud 2x2 grid: {} at {}\n{}", m, here, debugPrinter);
                    return result;
                }
                LOGGER.debug("dud 2x2 grid: {} at {}\n{}", m, here, debugPrinter);
                setVisited(here.x, here.y, false);
                if (last.x + last.y > here.x + here.y) {
                    delete(here);
                } else {
                    delete(Neighbour.SE.from(here));
                }
                here = last;
                last = null;
                LOGGER.debug("changed grid (m={}) and backtracked to {}\n{}", m, here, debugPrinter);
                continue;
            case 10:
                addToResult(result, Neighbour.E.fromX(here.x), Neighbour.E.fromY(here.y));
                addToResult(result, Neighbour.SE.fromX(here.x), Neighbour.SE.fromY(here.y));
                break;
            case 11:
                addToResult(result, here);
                addToResult(result, Neighbour.SE.fromX(here.x), Neighbour.SE.fromY(here.y));
                break;
            case 12:
                addToResult(result, Neighbour.SE.fromX(here.x), Neighbour.SE.fromY(here.y));
                addToResult(result, Neighbour.S.fromX(here.x), Neighbour.S.fromY(here.y));
                break;
            case 13:
                addToResult(result, Neighbour.SE.fromX(here.x), Neighbour.SE.fromY(here.y));
                addToResult(result, here);
                break;
            case 14:
                addToResult(result, Neighbour.E.fromX(here.x), Neighbour.E.fromY(here.y));
                addToResult(result, Neighbour.S.fromX(here.x), Neighbour.S.fromY(here.y));
                break;

            default:
                LOGGER.error("dud 2x2 grid: {} at {}\n{}", m, here, debugPrinter);
                return result;
            }
            last = here;
            here = MARCHDIRECTIONS[m].from(here);
        } while (!here.coincidesWith(start));

        return result;
    }

    private void delete(final Integer2DPoint pix) {
        grid.set(pix, false);
        setVisited(pix.x, pix.y, false);
    }

    private void addToResult(final Integer2DPolygon result, final Integer2DPoint pix) {
        if (!isVisited(pix.x, pix.y)) {
            result.add(pix);
            setVisited(pix.x, pix.y, true);
        }
    }

    private void addToResult(final Integer2DPolygon result, final int x, final int y) {
        if (!isVisited(x, y)) {
            result.add(new Integer2DPoint(x, y));
            setVisited(x, y, true);
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

    private void setVisited(final int x, final int y, final boolean v) {
        if (!isInside(x, y)) {
            throw new RuntimeException("attempt to set pixel beyond boundaries (" + size + "): >>" + x + ", " + y + "<<");
        }
        visited.set(pixelIndex(x, y), v);
    }

    private boolean isVisited(final int x, final int y) {
        if (!isInside(x, y)) {
            throw new RuntimeException("attempt to get pixel beyond boundaries (" + size + "): >>" + x + ", " + y + "<<");
        }
        return visited.get(pixelIndex(x, y));
    }

    /**
     * The index of a pixel in the 1D bit array.
     */
    private int pixelIndex(final int x, final int y) {
        return x * size.y + y;
    }

    private boolean isInside(final int x, final int y) {
        if (x < 0 || x >= size.x) {
            return false;
        }
        if (y < 0 || y >= size.y) {
            return false;
        }
        return true;
    }
}
