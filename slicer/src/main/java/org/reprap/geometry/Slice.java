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

}
