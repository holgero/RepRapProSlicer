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

import java.util.Stack;

import org.reprap.geometry.polygons.Point2D;

public class FloodFiller {
    private final Stack<Integer2DPoint> stack = new Stack<>();
    private final BooleanGrid grid;
    private final Integer2DRectangle rectangle;
    private final double pixelSize;
    private final String material;

    public FloodFiller(final BooleanGrid grid) {
        this.grid = grid;
        rectangle = grid.getRectangle();
        pixelSize = grid.getPixelSize();
        material = grid.getMaterial();
    }

    public BooleanGrid createFilledCopy(final Point2D realPoint) {
        final Integer2DPoint start = rectangle.convertToInteger2DPoint(realPoint, pixelSize);
        if (!grid.inside(start) || !grid.get(start)) {
            return BooleanGrid.NOTHING_THERE;
        }
        final BooleanGrid result = new BooleanGrid(pixelSize, material, new Integer2DRectangle(rectangle));
        result.set(start, true);
        stack.clear();
        stack.push(start);
        fill(result);
        return result;
    }

    private void fill(final BooleanGrid result) {
        while (!stack.isEmpty()) {
            final Integer2DPoint point = stack.pop();
            probePoint(result, new Integer2DPoint(point.getX(), point.getY() - 1));
            probePoint(result, new Integer2DPoint(point.getX() + 1, point.getY()));
            probePoint(result, new Integer2DPoint(point.getX(), point.getY() + 1));
            probePoint(result, new Integer2DPoint(point.getX() - 1, point.getY()));
        }
    }

    private void probePoint(final BooleanGrid result, final Integer2DPoint point) {
        if (grid.get(point) && !result.get(point)) {
            result.set(point, true);
            stack.push(point);
        }
    }

    public BooleanGrid findLand() {
        final Point2D seed = findFillStart();
        if (seed == null) {
            return BooleanGrid.NOTHING_THERE;
        }
        return createFilledCopy(seed);
    }

    public Point2D findFillStart() {
        final Integer2DPoint p = findSetPoint();
        if (p == null) {
            return null;
        } else {
            return rectangle.realPoint(p, pixelSize);
        }
    }

    private Integer2DPoint findSetPoint() {
        for (int x = 0; x < rectangle.getSizeX(); x++) {
            for (int y = 0; y < rectangle.getSizeY(); y++) {
                if (grid.get(x, y)) {
                    return new Integer2DPoint(x, y);
                }
            }
        }
        return null;
    }
}
