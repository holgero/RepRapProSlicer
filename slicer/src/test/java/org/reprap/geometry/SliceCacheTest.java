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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.reprap.geometry.polygons.BooleanGridList;

public class SliceCacheTest {

    private final SliceCache sliceCache = new SliceCache(3, 3);

    @Test
    public void testEmptyCache() {
        assertThat(sliceCache.getSlice(0, 0), is(nullValue()));
    }

    @Test
    public void testCacheHit() {
        final BooleanGridList slice00 = new BooleanGridList();
        sliceCache.setSlice(slice00, 0, 0);
        assertThat(sliceCache.getSlice(0, 0).getBitmaps(), is(slice00));
    }

    @Test
    public void testCacheMultipleSlicesOnOneLayer() {
        final BooleanGridList slice00 = new BooleanGridList();
        final BooleanGridList slice01 = new BooleanGridList();
        final BooleanGridList slice02 = new BooleanGridList();
        sliceCache.setSlice(slice00, 0, 0);
        sliceCache.setSlice(slice01, 0, 1);
        sliceCache.setSlice(slice02, 0, 2);
        assertThat(sliceCache.getSlice(0, 0).getBitmaps(), is(slice00));
        assertThat(sliceCache.getSlice(0, 1).getBitmaps(), is(slice01));
        assertThat(sliceCache.getSlice(0, 2).getBitmaps(), is(slice02));
    }

    @Test
    public void testCacheSlicesOnDifferentLayers() {
        final BooleanGridList slice00 = new BooleanGridList();
        final BooleanGridList slice10 = new BooleanGridList();
        final BooleanGridList slice20 = new BooleanGridList();
        sliceCache.setSlice(slice00, 0, 0);
        sliceCache.setSlice(slice10, 1, 0);
        sliceCache.setSlice(slice20, 2, 0);
        assertThat(sliceCache.getSlice(0, 0).getBitmaps(), is(slice00));
        assertThat(sliceCache.getSlice(1, 0).getBitmaps(), is(slice10));
        assertThat(sliceCache.getSlice(2, 0).getBitmaps(), is(slice20));
    }

    @Test
    public void testCacheCapacity() {
        final BooleanGridList slice = new BooleanGridList();
        sliceCache.setSlice(slice, 0, 0);
        sliceCache.setSlice(slice, 1, 0);
        sliceCache.setSlice(slice, 2, 0);
        sliceCache.setSlice(slice, 3, 0);
        assertThat(sliceCache.getSlice(0, 0), is(nullValue()));
        assertThat(sliceCache.getSlice(1, 0).getBitmaps(), is(slice));
        assertThat(sliceCache.getSlice(2, 0).getBitmaps(), is(slice));
        assertThat(sliceCache.getSlice(3, 0).getBitmaps(), is(slice));
        assertThat(sliceCache.getSlice(4, 0), is(nullValue()));
    }
}
