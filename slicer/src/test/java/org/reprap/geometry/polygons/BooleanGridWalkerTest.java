package org.reprap.geometry.polygons;

import static org.junit.Assert.assertEquals;
import static org.reprap.geometry.polygons.BooleanGridTest.emptyGrid;
import static org.reprap.geometry.polygons.BooleanGridTest.makeRectangleGrid;

import org.junit.Before;
import org.junit.Test;

public class BooleanGridWalkerTest {
    private static final boolean VISUALIZE = false;

    private final BooleanGrid testGrid = makeRectangleGrid(-0.4, -0.4, 0.4, 0.4);

    @Before
    public void setUp() throws Exception {
        assertEquals(34, testGrid.getRec().size.x);
        assertEquals(34, testGrid.getRec().size.y);
    }

    @Test
    public void testMarchSimpleRectangle() {
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(testGrid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(testGrid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
        final Integer2DPolygon polygon = simpleMarch.polygon(0);
        assertEquals(26 * 4, polygon.size());
        assertEquals(4, polygon.point(0).x); // Hmm, no equals() implementation...
        assertEquals(4, polygon.point(0).y);
        assertEquals(30, polygon.point(26).x);
        assertEquals(4, polygon.point(26).y);
        assertEquals(30, polygon.point(52).x);
        assertEquals(30, polygon.point(52).y);
    }

    @Test
    public void testMarchRectangleWithOneHole() {
        final BooleanGrid rectangleWithHole = BooleanGrid.difference(testGrid, testGrid.offset(-0.1));
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(rectangleWithHole).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(rectangleWithHole));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(2, simpleMarch.size());
    }

    @Test
    public void testMarchBigC() {
        final BooleanGrid cShape = BooleanGrid.difference(testGrid, makeRectangleGrid(-0.3, 0.3, -0.3, 0.5));
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(cShape).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(cShape));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchThreeIndependentRectangles() {
        final BooleanGrid threeRectangles = BooleanGrid.union(makeRectangleGrid(-0.4, -0.4, -0.3, -0.3),
                BooleanGrid.union(makeRectangleGrid(-0.4, 0, -0.3, 0.3), makeRectangleGrid(0, -0.3, 0.3, 0.1)));
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(threeRectangles).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(threeRectangles));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(3, simpleMarch.size());
    }

    @Test
    public void testMarchRectangleWithHorizontalCrack() {
        final int y = 10;
        for (int x = 4; x < 15; x++) {
            testGrid.set(new Integer2DPoint(x, y), false);
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(testGrid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(testGrid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchRectangleWithVerticalCrack() {
        final int x = 10;
        for (int y = 4; y < 15; y++) {
            testGrid.set(new Integer2DPoint(x, y), false);
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(testGrid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(testGrid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchRectangleWithCrackSlantedUp() {
        for (int c = 4; c < 15; c++) {
            testGrid.set(new Integer2DPoint(c, c), false);
        }
        if (VISUALIZE) {
            System.out.println(printGrid(testGrid));
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(testGrid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(testGrid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchRectangleWithCrackSlantedDown() {
        for (int c = 4; c < 15; c++) {
            testGrid.set(new Integer2DPoint(c, 30 - c), false);
        }
        if (VISUALIZE) {
            System.out.println(printGrid(testGrid));
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(testGrid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(testGrid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchSingleSlantedLine() {
        final BooleanGrid grid = emptyGrid();
        for (int c = 4; c < 15; c++) {
            grid.set(new Integer2DPoint(c, 30 - c), true);
            grid.set(new Integer2DPoint(c + 1, 30 - c), true);
        }
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(grid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchSingleHorizontalLine() {
        final BooleanGrid grid = emptyGrid();
        for (int x = 10; x < 25; x++) {
            grid.set(new Integer2DPoint(x, 20), true);
        }
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(grid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchSingleVerticalLine() {
        final BooleanGrid grid = emptyGrid();
        for (int y = 10; y < 25; y++) {
            grid.set(new Integer2DPoint(20, y), true);
        }
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(grid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(1, simpleMarch.size());
    }

    @Test
    public void testMarchEmptyGrid() {
        final BooleanGrid grid = emptyGrid();
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
        }
        final Integer2DPolygonList simpleMarch = new BooleanGridWalker(grid).marchAll();
        if (VISUALIZE) {
            System.out.println(printGrid(grid));
            System.out.println(printPolygonList(simpleMarch));
        }
        assertEquals(0, simpleMarch.size());
    }

    private String printPolygonList(final Integer2DPolygonList simpleMarch) {
        final BooleanGrid grid = emptyGrid();

        for (int i = 0; i < simpleMarch.size(); i++) {
            final Integer2DPolygon polygon = simpleMarch.polygon(i);
            for (int j = 0; j < polygon.size(); j++) {
                final Integer2DPoint point = polygon.point(j);
                grid.set(point, true);
            }
        }
        return printGrid(grid);
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