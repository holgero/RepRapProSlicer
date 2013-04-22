package org.reprap.geometry.polygons;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BooleanGridTest {
    private static final boolean VISUALIZE = false;

    static final BooleanGrid EMPTY = emptyGrid();

    static BooleanGrid makeRectangleGrid(final double minX, final double minY, final double maxX, final double maxY) {
        return new BooleanGrid(CSG2D.RrCSGFromBox(new Rectangle(new Point2D(minX, minY), new Point2D(maxX, maxY))),
                new Rectangle(new Point2D(0, 0), new Point2D(.01, .01)), null);
    }

    static BooleanGrid emptyGrid() {
        return new BooleanGrid(CSG2D.nothing(), new Rectangle(new Point2D(0, 0), new Point2D(.01, .01)), null);
    }

    @Test
    public void testAminusAisEmpty() throws Exception {
        final BooleanGrid gridA = makeRectangleGrid(0, 0, 0.3, 0.3);
        final BooleanGrid gridAminusA = BooleanGrid.difference(gridA, gridA);
        if (VISUALIZE) {
            System.out.println(printGrid(gridA));
            System.out.println(printGrid(gridAminusA));
        }
        // learned: A-A is not an empty grid of same size as A, but a "special" empty grid of size 1x1.
        // assertTrue("A minus A is not empty", gridEquals(EMPTY, gridAminusA));
        assertTrue("A minus A isEmpty", gridAminusA.isEmpty());
    }

    @Test
    public void testAminusEmptyisA() throws Exception {
        final BooleanGrid gridA = makeRectangleGrid(0, 0, 0.3, 0.3);
        final BooleanGrid gridEmptyminusA = BooleanGrid.difference(gridA, EMPTY);
        if (VISUALIZE) {
            System.out.println(printGrid(gridA));
            System.out.println(printGrid(gridEmptyminusA));
        }
        assertTrue("A minus empty is not A", gridEquals(gridA, gridEmptyminusA));
    }

    static boolean gridEquals(final BooleanGrid gridA, final BooleanGrid gridB) {
        if (!rectangleEquals(gridA.getRec(), gridB.getRec())) {
            return false;
        }
        final Integer2DPoint sizeA = gridA.getRec().size;
        for (int x = 0; x < sizeA.x; x++) {
            for (int y = 0; y < sizeA.y; y++) {
                final Integer2DPoint point = new Integer2DPoint(x, y);
                if (gridA.get(point) != gridB.get(point)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean rectangleEquals(final Integer2DRectangle recA, final Integer2DRectangle recB) {
        return pointEquals(recA.swCorner, recB.swCorner) && pointEquals(recA.size, recB.size);
    }

    private static boolean pointEquals(final Integer2DPoint pointA, final Integer2DPoint pointB) {
        return pointA.x == pointB.x && pointA.y == pointB.y;
    }

    @Test
    public void testRectangleDifference() throws Exception {
        final BooleanGrid gridA = makeRectangleGrid(0, 0, 0.3, 0.3);
        final BooleanGrid gridB = makeRectangleGrid(0.2, 0, 0.3, 0.3);
        final BooleanGrid gridAminusB = BooleanGrid.difference(gridA, gridB);
        if (VISUALIZE) {
            System.out.println(printGrid(gridA));
            System.out.println(printGrid(gridB));
            System.out.println(printGrid(gridAminusB));
        }
    }

    private String printGrid(final BooleanGrid grid) {
        final StringBuilder output = new StringBuilder();
        final Integer2DPoint size = grid.getRec().size;
        for (int y = size.y - 1; y >= 0; y--) {
            for (int x = 0; x < size.x; x++) {
                //                output.append(" ");
                final Integer2DPoint printPoint = new Integer2DPoint(x, y);
                if (grid.get(printPoint)) {
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
