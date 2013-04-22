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

import java.util.ArrayList;
import java.util.List;

import org.reprap.geometry.polygons.Interval;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Polygon;

class ExtrusionPath {
    private final Polygon path;
    /**
     * The speed of the machine at each corner
     */
    private final List<Double> speeds = new ArrayList<Double>();
    /**
     * The index of the last point to draw to, if there are more that should
     * just be moved over
     */
    private int extrudeEnd = -1;
    /**
     * The index of the last point at which the valve (if any) is open.
     */
    private int valveEnd = -1;

    ExtrusionPath(final Polygon path) {
        this.path = path;
        for (int i = 0; i < path.size(); i++) {
            speeds.add(Double.valueOf(0.0));
        }
        validate();
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
                p = Point2D.add(path.point(i), Point2D.mul(sum / q.mod(), q));
                double s = 0;
                s = speeds.get(last) - speeds.get(i);
                s = speeds.get(i) + s * sum / q.mod();
                final int j = i + 1;
                if (j < path.size()) {
                    path.add(j, p);
                    speeds.add(j, new Double(s));
                } else {
                    path.add(p);
                    speeds.add(new Double(s));
                }
                extrudeEnd = j;
                break;
            }
            last = i;
        }
    }

    void backStepValve(final double valveBackLength) {
        if (valveBackLength <= 0) {
            return;
        }
        Point2D p, q;
        int start, last;

        if (valveEnd >= 0) {
            start = valveEnd;
        } else {
            start = path.size() - 1;
        }

        if (!path.isClosed() && valveEnd < 0) {
            start--;
        }

        if (start >= path.size() - 1) {
            last = 0;
        } else {
            last = start + 1;
        }

        valveEnd = 0;
        double sum = 0;
        for (int i = start; i >= 0; i--) {
            sum += Point2D.d(path.point(i), path.point(last));
            if (sum > valveBackLength) {
                sum = sum - valveBackLength;
                q = Point2D.sub(path.point(last), path.point(i));
                p = Point2D.add(path.point(i), Point2D.mul(sum / q.mod(), q));
                double s = 0;
                s = speeds.get(last) - speeds.get(i);
                s = speeds.get(i) + s * sum / q.mod();
                final int j = i + 1;
                if (j < path.size()) {
                    path.add(j, p);
                    speeds.add(j, new Double(s));
                } else {
                    path.add(p);
                    speeds.add(new Double(s));
                }
                valveEnd = j;
                break;
            }
            last = i;
        }
    }

    Point2D point(final int i) {
        return path.point(i);
    }

    double speed(final int i) {
        return speeds.get(i).doubleValue();
    }

    int size() {
        return path.size();
    }

    int extrudeEnd() {
        return positiveIndexOrLast(extrudeEnd);
    }

    int valveEnd() {
        return positiveIndexOrLast(valveEnd);
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

    /**
     * Set the extrusion speed for this path segment.
     */
    void setSpeed(final int i, final double speed) {
        speeds.set(i, Double.valueOf(speed));
    }

    static Interval accRange(final double startVelocity, final double distance, final double acceleration) {
        final double vMax = Math.sqrt(2 * acceleration * distance + startVelocity * startVelocity);
        double vMin = -2 * acceleration * distance + startVelocity * startVelocity;
        if (vMin <= 0) {
            vMin = 0;
        } else {
            vMin = Math.sqrt(vMin);
        }
        return new Interval(vMin, vMax);
    }

    void backTrack(int i, double vCorner, final double acceleration, final boolean[] fixup) {
        Point2D a, b, ab;
        double backV, s;
        int i1 = i - 1;
        b = path.point(i);
        while (i1 >= 0) {
            a = path.point(i1);
            ab = Point2D.sub(b, a);
            s = ab.mod();
            ab = Point2D.div(ab, s);
            backV = Math.sqrt(vCorner * vCorner + 2 * acceleration * s);
            setSpeed(i, vCorner);
            if (backV > speed(i1)) {
                fixup[i] = true;
                break;
            }
            setSpeed(i1, backV);
            vCorner = backV;
            fixup[i] = false;
            b = a;
            i = i1;
            i1--;
        }
    }

    void add(final int i, final Point2D add, final double v) {
        path.add(i, add);
        speeds.add(i, v);

        if (i <= extrudeEnd) {
            extrudeEnd++;
        }
        if (i <= valveEnd) {
            valveEnd++;
        }
    }

    void validate() {
        if (speeds.size() != path.size()) {
            throw new RuntimeException("Speeds arrays differs from path size: " + speeds.size() + ", " + path.size());
        }
    }
}
