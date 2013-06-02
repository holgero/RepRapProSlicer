/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *  separated extrusion specific parts from org.reprap.geometry.polygons.Polygon,
 *  which is Copyright (C) 2005 Adrian Bowyer & The University of Bath
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

import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;

class ExtrusionPath {
    private final Polygon path;
    /**
     * The index of the last point to draw to, if there are more that should
     * just be moved over
     */
    private int extrudeEnd = -1;

    ExtrusionPath(final Polygon path) {
        this.path = path;
    }

    void backStepExtrude(final double extrudeBackLength) {
        if (extrudeBackLength <= 0) {
            return;
        }
        Point2D p, q;
        int start, last;

        if (extrudeEnd >= 0) {
            start = extrudeEnd;
        } else {
            start = path.size() - 1;
        }

        if (!path.isClosed() && extrudeEnd < 0) {
            start--;
        }

        if (start >= path.size() - 1) {
            last = 0;
        } else {
            last = start + 1;
        }

        double sum = 0;
        extrudeEnd = 0;
        for (int i = start; i >= 0; i--) {
            sum += Point2D.d(path.point(i), path.point(last));
            if (sum > extrudeBackLength) {
                sum = sum - extrudeBackLength;
                q = Point2D.sub(path.point(last), path.point(i));
                p = Point2D.add(path.point(i), Point2D.mul(q, sum / q.mod()));
                final int j = i + 1;
                if (j < path.size()) {
                    path.add(j, p);
                } else {
                    path.add(p);
                }
                extrudeEnd = j;
                break;
            }
            last = i;
        }
    }

    Point2D point(final int i) {
        return path.point(i);
    }

    int size() {
        return path.size();
    }

    int extrudeEnd() {
        return positiveIndexOrLast(extrudeEnd);
    }

    private int positiveIndexOrLast(final int index) {
        if (index < 0) {
            return size() - 1;
        } else {
            return index;
        }
    }

    boolean isClosed() {
        return path.isClosed();
    }
}
