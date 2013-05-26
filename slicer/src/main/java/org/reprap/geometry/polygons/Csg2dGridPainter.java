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

import java.util.BitSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Csg2dGridPainter {
    private static final Logger LOGGER = LogManager.getLogger(Csg2dGridPainter.class);

    private final double pixelSize;
    private final Integer2DRectangle rectangle;
    private final BitSet bits;

    private final Point2D increment;

    public Csg2dGridPainter(final double pixelSize, final Integer2DRectangle rectangle, final BitSet bits) {
        this.pixelSize = pixelSize;
        this.rectangle = rectangle;
        this.bits = bits;
        increment = new Point2D(pixelSize / 2, pixelSize / 2);
    }

    public void paint(final CSG2D csgExp) {
        generateQuadTree(new Integer2DPoint(0, 0), new Integer2DPoint(rectangle.getSizeX() - 1, rectangle.getSizeY() - 1),
                csgExp);
    }

    /**
     * Generate the entire image from a CSG expression recursively using a quad
     * tree.
     */
    private void generateQuadTree(final Integer2DPoint ipsw, final Integer2DPoint ipne, final CSG2D csgExpression) {
        final Point2D p0 = rectangle.realPoint(ipsw, pixelSize);

        if (samePixel(ipsw, ipne)) {
            final boolean value = csgExpression.value(p0) <= 0;
            bits.set(ipsw.getX() * rectangle.getSizeY() + ipsw.getY(), value);
            return;
        }

        final Point2D p1 = rectangle.realPoint(ipne, pixelSize);
        final Interval i = csgExpression.value(new Rectangle(Point2D.sub(p0, increment), Point2D.add(p1, increment)));
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
            callGenerateQuadTree(x0, y0, x0, ym, csgExpression);
            callGenerateQuadTree(x0, ym + 1, x0, y1, csgExpression);
            return;
        }

        // Special case - a single horizontal line of pixels
        if (yd <= 1) {
            callGenerateQuadTree(x0, y0, xm, y0, csgExpression);
            callGenerateQuadTree(xm + 1, y0, x1, y0, csgExpression);
            return;
        }

        // General case - 4 quads.
        callGenerateQuadTree(x0, y0, xm, ym, csgExpression);
        callGenerateQuadTree(x0, ym + 1, xm, y1, csgExpression);
        callGenerateQuadTree(xm + 1, ym + 1, x1, y1, csgExpression);
        callGenerateQuadTree(xm + 1, y0, x1, ym, csgExpression);
    }

    private static boolean samePixel(final Integer2DPoint ipsw, final Integer2DPoint ipne) {
        return ipsw.coincidesWith(ipne);
    }

    private void callGenerateQuadTree(final int swX, final int swY, final int neX, final int neY, final CSG2D csgExpression) {
        final Integer2DPoint sw = new Integer2DPoint(swX, swY);
        final Integer2DPoint ne = new Integer2DPoint(neX, neY);
        final Rectangle realRectangle = new Rectangle(Point2D.sub(rectangle.realPoint(sw, pixelSize), increment), Point2D.add(
                rectangle.realPoint(ne, pixelSize), increment));
        generateQuadTree(sw, ne, csgExpression.prune(realRectangle));
    }

    /**
     * Set a whole rectangle to one value
     */
    private void homogeneous(final Integer2DPoint ipsw, final Integer2DPoint ipne, final boolean v) {
        for (int x = ipsw.getX(); x <= ipne.getX(); x++) {
            final int startX = x * rectangle.getSizeY();
            bits.set(startX + ipsw.getY(), startX + ipne.getY() + 1, v);
        }
    }
}
