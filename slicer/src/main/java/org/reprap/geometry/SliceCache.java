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

import org.reprap.geometry.polygons.BooleanGrid;

/**
 * Ring buffer cache to hold previously computed slices for doing infill and
 * support material calculations.
 * 
 * @author ensab
 */
final class SliceCache {
    private static final int NO_LAYER = Integer.MIN_VALUE;
    private final BooleanGrid[][] supportRing;
    private final int[] layerNumber;
    private int ringPointer;
    private final int ringSize;

    SliceCache(final int ringSize, final int size) {
        this.ringSize = ringSize;
        supportRing = new BooleanGrid[ringSize][size];
        layerNumber = new int[ringSize];
        ringPointer = 0;
        for (int layer = 0; layer < ringSize; layer++) {
            for (int stl = 0; stl < size; stl++) {
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
        for (int s = 0; s < supportRing[rp].length; s++) {
            supportRing[rp][s] = null;
        }
        ringPointer++;
        if (ringPointer >= ringSize) {
            ringPointer = 0;
        }
        return rp;
    }

    void setSupport(final BooleanGrid support, final int layer, final int stl) {
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

    BooleanGrid getSupport(final int layer, final int stl) {
        final int rp = getTheRingLocationForRead(layer);
        if (rp >= 0) {
            return supportRing[rp][stl];
        }
        return null;
    }
}