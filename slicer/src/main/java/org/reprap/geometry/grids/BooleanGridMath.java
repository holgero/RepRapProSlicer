/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *  contains code copied from BooleanGrid
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BooleanGridMath {
    private static final Logger LOGGER = LogManager.getLogger(BooleanGridMath.class);

    /**
     * Compute the union of two bit patterns, forcing material on the result.
     */
    public static BooleanGrid union(final BooleanGrid lh, final BooleanGrid rh) {
        if (lh == BooleanGrid.NOTHING_THERE) {
            if (rh == BooleanGrid.NOTHING_THERE) {
                return BooleanGrid.NOTHING_THERE;
            }
            return rh;
        }

        if (rh == BooleanGrid.NOTHING_THERE) {
            return lh;
        }

        final BooleanGrid result;
        if (lh.getRectangle().coincidesWith(rh.getRectangle())) {
            result = new BooleanGrid(lh);
            result.unionWith(rh);
        } else {
            final Integer2DRectangle u = lh.getRectangle().union(rh.getRectangle());
            result = new BooleanGrid(lh, u);
            result.unionWith(new BooleanGrid(rh, u));
        }
        if (lh.getMaterial() != rh.getMaterial()) {
            LOGGER.error("attempt to union two bitmaps of different materials: " + lh.getMaterial() + " and "
                    + rh.getMaterial());
        }
        return result;
    }

    /**
     * Compute the intersection of two bit patterns
     */
    public static BooleanGrid intersection(final BooleanGrid d, final BooleanGrid e) {
        if (d == BooleanGrid.NOTHING_THERE || e == BooleanGrid.NOTHING_THERE) {
            return BooleanGrid.NOTHING_THERE;
        }

        BooleanGrid result;
        if (d.getRectangle().coincidesWith(e.getRectangle())) {
            result = new BooleanGrid(d);
            result.intersectWith(e);
        } else {
            final Integer2DRectangle u = d.getRectangle().intersection(e.getRectangle());
            if (u.isEmpty()) {
                return BooleanGrid.NOTHING_THERE;
            }
            result = new BooleanGrid(d, u);
            final BooleanGrid temp = new BooleanGrid(e, u);
            result.intersectWith(temp);
        }
        if (result.isEmpty()) {
            return BooleanGrid.NOTHING_THERE;
        }
        if (!d.getMaterial().equals(e.getMaterial())) {
            LOGGER.error("attempt to intersect two bitmaps of different materials: " + d.getMaterial() + " and "
                    + e.getMaterial());
        }
        return result;
    }

    /**
     * Grid d - grid e, forcing attribute a on the result d's rectangle is
     * presumed to contain the result. TODO: write a function to compute the
     * rectangle from the bitmap
     */
    public static BooleanGrid difference(final BooleanGrid lh, final BooleanGrid rh) {
        if (lh == BooleanGrid.NOTHING_THERE) {
            return BooleanGrid.NOTHING_THERE;
        }

        if (rh == BooleanGrid.NOTHING_THERE) {
            return lh;
        }

        final BooleanGrid result = new BooleanGrid(lh);
        BooleanGrid temp;
        if (lh.getRectangle().coincidesWith(rh.getRectangle())) {
            temp = rh;
        } else {
            temp = new BooleanGrid(rh, result.getRectangle());
        }
        result.substract(temp);
        if (result.isEmpty()) {
            return BooleanGrid.NOTHING_THERE;
        }
        if (lh.getMaterial() != rh.getMaterial()) {
            LOGGER.error("attempt to subtract two bitmaps of different materials: " + lh.getMaterial() + " and "
                    + rh.getMaterial());
        }
        return result;
    }
}
