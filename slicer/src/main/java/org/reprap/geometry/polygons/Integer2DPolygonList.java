/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *   originally extracted from BooleanGrid 
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

import java.util.ArrayList;
import java.util.List;

/**
 * A list of polygons
 */
final class Integer2DPolygonList {
    private List<Integer2DPolygon> polygons = null;

    Integer2DPolygonList() {
        polygons = new ArrayList<Integer2DPolygon>();
    }

    /**
     * Return the ith polygon
     */
    Integer2DPolygon polygon(final int i) {
        return polygons.get(i);
    }

    /**
     * How many polygons are there in the list?
     */
    int size() {
        return polygons.size();
    }

    /**
     * Add a polygon on the end
     */
    void add(final Integer2DPolygon p) {
        polygons.add(p);
    }

    /**
     * Replace a polygon in the list
     */
    void set(final int i, final Integer2DPolygon p) {
        polygons.set(i, p);
    }

    /**
     * Get rid of a polygon from the list
     */
    void remove(final int i) {
        polygons.remove(i);
    }

    /**
     * Translate by vector t
     */
    Integer2DPolygonList translate(final Integer2DPoint t) {
        final Integer2DPolygonList result = new Integer2DPolygonList();
        for (int i = 0; i < size(); i++) {
            result.add(polygon(i).translate(t));
        }
        return result;
    }

    /**
     * Turn all the polygons into real-world polygons
     */
    PolygonList realPolygons(final String material, final Integer2DRectangle rec, final double pixelSize) {
        final PolygonList result = new PolygonList();
        for (int i = 0; i < size(); i++) {
            result.add(polygon(i).realPolygon(material, rec, pixelSize));
        }
        return result;
    }

    /**
     * Simplify all the polygons
     */
    Integer2DPolygonList simplify() {
        final Integer2DPolygonList result = new Integer2DPolygonList();
        for (int i = 0; i < size(); i++) {
            result.add(polygon(i).simplify());
        }
        return result;
    }
}