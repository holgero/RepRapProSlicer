/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *   most coding has been extracted from 
 *   org.reprap.geometry.polyhedra.AllSTLsToBuild
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

import org.reprap.geometry.polygons.BooleanGridList;

/**
 * Ring buffer cache to hold previously computed slices for doing infill and
 * support material calculations.
 * 
 * @author ensab
 */
final class SliceCache {
    private static final int NO_LAYER = Integer.MIN_VALUE;
    private final BooleanGridList[][] sliceRing;
    private final BooleanGridList[][] supportRing;
    private final int[] layerNumber;
    private int ringPointer;
    private final int ringSize;

    SliceCache(final int ringSize, final int size) {
        this.ringSize = ringSize;
        sliceRing = new BooleanGridList[ringSize][size];
        supportRing = new BooleanGridList[ringSize][size];
        layerNumber = new int[ringSize];
        ringPointer = 0;
        for (int layer = 0; layer < ringSize; layer++) {
            for (int stl = 0; stl < size; stl++) {
                sliceRing[layer][stl] = null;
                supportRing[layer][stl] = null;
                layerNumber[layer] = NO_LAYER;
            }
        }
    }

    private int getTheRingLocationForWrite(final int layer) {
        for (int i = 0; i < ringSize; i++) {
            if (layerNumber[i] == layer) {
                return i;
            }
        }

        final int rp = ringPointer;
        for (int s = 0; s < sliceRing[rp].length; s++) {
            sliceRing[rp][s] = null;
            supportRing[rp][s] = null;
        }
        ringPointer++;
        if (ringPointer >= ringSize) {
            ringPointer = 0;
        }
        return rp;
    }

    void setSlice(final BooleanGridList slice, final int layer, final int stl) {
        final int rp = getTheRingLocationForWrite(layer);
        layerNumber[rp] = layer;
        sliceRing[rp][stl] = slice;
    }

    void setSupport(final BooleanGridList support, final int layer, final int stl) {
        final int rp = getTheRingLocationForWrite(layer);
        layerNumber[rp] = layer;
        supportRing[rp][stl] = support;
    }

    private int getTheRingLocationForRead(final int layer) {
        int rp = ringPointer;
        for (int i = 0; i < ringSize; i++) {
            rp--;
            if (rp < 0) {
                rp = ringSize - 1;
            }
            if (layerNumber[rp] == layer) {
                return rp;
            }
        }
        return -1;
    }

    Slice getSlice(final int layer, final int stl) {
        final int rp = getTheRingLocationForRead(layer);
        if (rp >= 0) {
            return new Slice(sliceRing[rp][stl]);
        }
        return null;
    }

    BooleanGridList getSupport(final int layer, final int stl) {
        final int rp = getTheRingLocationForRead(layer);
        if (rp >= 0) {
            return supportRing[rp][stl];
        }
        return null;
    }
}