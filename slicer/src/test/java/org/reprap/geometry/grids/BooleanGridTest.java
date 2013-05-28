/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
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

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Rectangle;

public class BooleanGridTest {
    private static final double PIXELSIZE = 0.03;

    private static final boolean VISUALIZE = false;

    static final BooleanGrid EMPTY = emptyGrid();

    static BooleanGrid makeRectangleGrid(final double minX, final double minY, final double maxX, final double maxY) {
        return new BooleanGrid(PIXELSIZE, null, new Rectangle(new Point2D(0, 0), new Point2D(.01, .01)),
                CSG2D.RrCSGFromBox(new Rectangle(new Point2D(minX, minY), new Point2D(maxX, maxY))));
    }

    static BooleanGrid emptyGrid() {
        return new BooleanGrid(PIXELSIZE, null, new Rectangle(new Point2D(0, 0), new Point2D(.01, .01)), CSG2D.nothing());
    }

    @Test
    public void testAminusAisEmpty() {
        final BooleanGrid gridA = makeRectangleGrid(0, 0, 0.3, 0.3);
        final BooleanGrid gridAminusA = BooleanGridMath.difference(gridA, gridA);
        if (VISUALIZE) {
            System.out.println(printGrid(gridA));
            System.out.println(printGrid(gridAminusA));
        }
        // learned: A-A is not an empty grid of same size as A, but a "special" empty grid of size 1x1.
        // assertTrue("A minus A is not empty", gridEquals(EMPTY, gridAminusA));
        assertTrue("A minus A isEmpty", gridAminusA.isEmpty());
    }

    @Test
    public void testAminusEmptyisA() {
        final BooleanGrid gridA = makeRectangleGrid(0, 0, 0.3, 0.3);
        final BooleanGrid gridEmptyminusA = BooleanGridMath.difference(gridA, EMPTY);
        if (VISUALIZE) {
            System.out.println(printGrid(gridA));
            System.out.println(printGrid(gridEmptyminusA));
        }
        assertTrue("A minus empty is not A", gridEquals(gridA, gridEmptyminusA));
    }

    static boolean gridEquals(final BooleanGrid gridA, final BooleanGrid gridB) {
        if (!rectangleEquals(gridA.getRectangle(), gridB.getRectangle())) {
            return false;
        }
        for (int x = 0; x < gridA.getRectangle().getSizeX(); x++) {
            for (int y = 0; y < gridA.getRectangle().getSizeY(); y++) {
                final Integer2DPoint point = new Integer2DPoint(x, y);
                if (gridA.get(point) != gridB.get(point)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean rectangleEquals(final Integer2DRectangle recA, final Integer2DRectangle recB) {
        return pointEquals(recA.getSwCorner(), recB.getSwCorner()) && recA.getSizeX() == recB.getSizeX()
                && recA.getSizeY() == recB.getSizeY();
    }

    private static boolean pointEquals(final Integer2DPoint pointA, final Integer2DPoint pointB) {
        return pointA.getX() == pointB.getX() && pointA.getY() == pointB.getY();
    }

    @Test
    public void testRectangleDifference() {
        final BooleanGrid gridA = makeRectangleGrid(0, 0, 0.3, 0.3);
        final BooleanGrid gridB = makeRectangleGrid(0.2, 0, 0.3, 0.3);
        final BooleanGrid gridAminusB = BooleanGridMath.difference(gridA, gridB);
        if (VISUALIZE) {
            System.out.println(printGrid(gridA));
            System.out.println(printGrid(gridB));
            System.out.println(printGrid(gridAminusB));
        }
    }

    private static String printGrid(final BooleanGrid grid) {
        final StringBuilder output = new StringBuilder();
        for (int y = grid.getRectangle().getSizeY() - 1; y >= 0; y--) {
            for (int x = 0; x < grid.getRectangle().getSizeX(); x++) {
                if (grid.get(x, y)) {
                    output.append("*");
                } else {
                    output.append(".");
                }
            }
            output.append("\n");
        }
        return output.toString();
    }
}
