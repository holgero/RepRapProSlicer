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
package org.reprap.geometry;

import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.BooleanGridList;
import org.reprap.geometry.polygons.PolygonList;

class Slice {
    private final BooleanGridList bitmaps;

    Slice(final BooleanGridList bitmaps) {
        if (bitmaps == null) {
            throw new IllegalArgumentException("bitmaps must not be null");
        }
        this.bitmaps = bitmaps;
    }

    BooleanGridList getBitmaps(final String material) {
        final BooleanGridList result = new BooleanGridList();
        for (final BooleanGrid grid : bitmaps) {
            if (grid.getMaterial().equals(material)) {
                result.add(grid);
            }
        }
        return result;
    }

    BooleanGridList getBitmaps() {
        return bitmaps;
    }

    PolygonList computeBrim(final int brimLines, final double extrusionSize) {
        BooleanGridList brimOutline = bitmaps;
        final PolygonList result = new PolygonList();
        result.add(brimOutline.borders());
        for (int line = 1; line < brimLines; line++) {
            final BooleanGridList nextOutline = new BooleanGridList();
            for (int i = 0; i < brimOutline.size(); i++) {
                final BooleanGrid grid = brimOutline.get(i);
                nextOutline.add(grid.createOffsetGrid(extrusionSize));
            }
            brimOutline = nextOutline;
            result.add(brimOutline.borders());
        }
        return result;
    }

    BooleanGrid unionMaterials() {
        if (bitmaps.size() == 0) {
            return BooleanGrid.nullBooleanGrid();
        }
        BooleanGrid union = bitmaps.get(0);
        for (int i = 1; i < bitmaps.size(); i++) {
            union = BooleanGrid.union(union, bitmaps.get(i), union.getMaterial());
        }
        return union;
    }

    BooleanGridList getSliceWithoutBorder(final String material, final double extrusionSize, final int shells,
            final double infillOverlap) {
        final BooleanGridList gridList = getBitmaps(material);
        if (gridList.size() <= 0) {
            return new BooleanGridList();
        }
        final BooleanGridList result = new BooleanGridList();
        for (int i = 0; i < gridList.size(); i++) {
            final BooleanGrid grid = gridList.get(i);
            for (double offset = -(shells + 0.5) * extrusionSize + infillOverlap; offset < 0; offset += extrusionSize) {
                final BooleanGrid borderGrid = grid.createOffsetGrid(offset);
                if (!borderGrid.isEmpty()) {
                    result.add(borderGrid);
                    break;
                }
            }
        }
        return result;
    }

    BooleanGridList getOutlineGrids(final String material, final boolean insideOut, final int shells, final double extrusionSize) {
        final BooleanGridList gridList = getBitmaps(material);
        if (gridList.size() <= 0) {
            return gridList;
        }
        final BooleanGridList result = new BooleanGridList();
        for (int i = 0; i < gridList.size(); i++) {
            final BooleanGrid grid = gridList.get(i);
            final BooleanGridList offset = offsetOutline(grid, shells, extrusionSize);
            if (insideOut) {
                offset.reverse();
            }
            for (int j = 0; j < offset.size(); j++) {
                result.add(offset.get(j));
            }
        }
        return result;
    }

    private static BooleanGridList offsetOutline(final BooleanGrid grid, final int shells, final double extrusionSize) {
        final BooleanGridList result = new BooleanGridList();
        for (int shell = 0; shell < shells; shell++) {
            final double offset = -(shell + 0.5) * extrusionSize;
            final BooleanGrid thisOne = grid.createOffsetGrid(offset);
            if (thisOne.isEmpty()) {
                break;
            } else {
                result.add(thisOne);
            }
        }
        return result;
    }

}
