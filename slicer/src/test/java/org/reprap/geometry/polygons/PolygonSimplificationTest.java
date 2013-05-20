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
package org.reprap.geometry.polygons;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.junit.Assert.assertThat;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;

public class PolygonSimplificationTest {
    private static final double[][] CIRCLE_POLYGON_DATA = new double[][] { { 69.78, 101.97 }, { 69.81, 101.88 },
            { 69.84, 101.31 }, { 69.86999999999999, 101.22 }, { 69.96, 100.28999999999999 }, { 70.14, 98.64 },
            { 70.2, 98.03999999999999 }, { 71.25, 94.8 }, { 71.39999999999999, 94.32 }, { 71.58, 93.99 }, { 72.03, 93.21 },
            { 73.47, 90.69 }, { 74.03999999999999, 90.06 }, { 75.39, 88.56 }, { 75.81, 88.11 }, { 75.89999999999999, 87.99 },
            { 76.08, 87.81 }, { 76.17, 87.69 }, { 77.42999999999999, 86.78999999999999 }, { 79.32, 85.41 }, { 83.1, 83.73 },
            { 86.28, 83.07 }, { 86.91, 82.92 }, { 87.11999999999999, 82.89 }, { 89.03999999999999, 82.89 }, { 89.13, 82.92 },
            { 91.11, 82.92 }, { 91.2, 82.95 }, { 94.11, 83.55 }, { 94.22999999999999, 83.58 }, { 94.8, 83.7 },
            { 97.41, 84.84 }, { 98.72999999999999, 85.47 }, { 99.21, 85.8 }, { 101.42999999999999, 87.42 }, { 101.94, 87.81 },
            { 102.11999999999999, 87.99 }, { 102.3, 88.22999999999999 }, { 102.83999999999999, 88.8 },
            { 102.92999999999999, 88.92 }, { 103.11, 89.1 }, { 103.2, 89.22 }, { 103.89, 89.97 }, { 104.46, 90.6 },
            { 104.78999999999999, 91.11 }, { 106.61999999999999, 94.38 }, { 107.19, 96.17999999999999 }, { 107.82, 98.13 },
            { 107.88, 98.67 }, { 107.91, 99.0 }, { 107.94, 99.3 }, { 107.97, 99.81 }, { 108.0, 99.89999999999999 },
            { 108.06, 100.41 }, { 108.08999999999999, 100.92 }, { 108.11999999999999, 101.00999999999999 },
            { 108.14999999999999, 101.58 }, { 108.17999999999999, 101.67 }, { 108.17999999999999, 101.82 }, { 108.21, 101.91 },
            { 108.21, 102.21 }, { 108.17999999999999, 102.3 }, { 108.08999999999999, 103.22999999999999 }, { 107.91, 104.88 },
            { 107.85, 105.36 }, { 107.82, 105.63 }, { 107.82, 105.83999999999999 }, { 107.61, 106.61999999999999 },
            { 106.58999999999999, 109.71 }, { 106.32, 110.25 }, { 104.46, 113.36999999999999 }, { 104.33999999999999, 113.52 },
            { 103.74, 114.17999999999999 }, { 103.47, 114.47999999999999 }, { 103.2, 114.78 }, { 102.92999999999999, 115.08 },
            { 102.08999999999999, 116.00999999999999 }, { 101.82, 116.31 }, { 100.28999999999999, 117.42 },
            { 99.09, 118.25999999999999 }, { 98.97, 118.35 }, { 98.85, 118.47 }, { 98.1, 118.86 }, { 94.77, 120.3 },
            { 92.16, 120.83999999999999 }, { 91.17, 121.05 }, { 90.84, 121.11 }, { 86.97, 121.11 }, { 86.64, 121.02 },
            { 85.17, 120.72 }, { 84.63, 120.6 }, { 82.98, 120.24 }, { 79.25999999999999, 118.53 }, { 76.44, 116.49 },
            { 76.17, 116.28 }, { 76.05, 116.16 }, { 75.78, 115.86 }, { 75.50999999999999, 115.56 },
            { 75.24, 115.25999999999999 }, { 74.94, 114.92999999999999 }, { 74.67, 114.66 }, { 74.58, 114.53999999999999 },
            { 74.13, 114.06 }, { 74.03999999999999, 113.94 }, { 73.59, 113.46 }, { 73.47, 113.31 },
            { 71.39999999999999, 109.71 }, { 70.64999999999999, 107.31 }, { 70.2, 105.92999999999999 }, { 70.2, 105.75 },
            { 70.17, 105.66 }, { 70.17, 105.50999999999999 }, { 70.14, 105.42 }, { 70.14, 105.27 },
            { 70.11, 105.17999999999999 }, { 70.08, 104.91 }, { 70.08, 104.61 }, { 70.05, 104.52 },
            { 69.92999999999999, 103.53 }, { 69.89999999999999, 103.08 }, { 69.86999999999999, 102.96 },
            { 69.84, 102.53999999999999 }, { 69.81, 102.14999999999999 }, { 69.78, 102.0 } };

    @Test
    public void testSimplifiedCircleHasSameBound() {
        final Polygon circle = createPolygon(CIRCLE_POLYGON_DATA);
        final Polygon simplified = circle.simplify(0.045);
        assertThat(simplified.getBox(), is(sameRectangle(circle.getBox())));
    }

    @Test
    public void testDoubleSimplifiedCircleStaysTheSame() {
        final Polygon circle = createPolygon(CIRCLE_POLYGON_DATA);
        final Polygon simplified1 = circle.simplify(0.045);
        final Polygon simplified2 = simplified1.simplify(0.045);
        assertThat(simplified2.getBox(), is(sameRectangle(simplified1.getBox())));
        assertThat(simplified2.size(), is(simplified1.size()));
    }

    private static RectangleMatcher sameRectangle(final Rectangle rectangle) {
        return new RectangleMatcher(rectangle);
    }

    private static final class RectangleMatcher extends BaseMatcher<Rectangle> {
        private static final double TINY = 1e-10;
        private final Rectangle thisOne;

        RectangleMatcher(final Rectangle thisOne) {
            this.thisOne = thisOne;
        }

        @Override
        public boolean matches(final Object item) {
            if (item instanceof Rectangle) {
                final Rectangle other = (Rectangle) item;
                return pointCloseTo(thisOne.ne(), other.ne()) && pointCloseTo(thisOne.sw(), other.sw());
            }
            return false;
        }

        private static boolean pointCloseTo(final Point2D thisPoint, final Point2D otherPoint) {
            return closeTo(thisPoint.x(), TINY).matches(otherPoint.x()) && closeTo(thisPoint.y(), TINY).matches(otherPoint.y());
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("a point within ").appendValue(TINY).appendText(" of ").appendValue(thisOne);
        }
    }

    private static Polygon createPolygon(final double[][] circlePolygonData) {
        final Polygon result = new Polygon("PLA", true);
        for (final double[] pointData : circlePolygonData) {
            result.add(new Point2D(pointData[0], pointData[1]));
        }
        return result;
    }

}
