/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *  contains code extracted from Polygon Copyright (C) 2005 Adrian Bowyer & The University of Bath
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

import org.reprap.configuration.store.MathRoutines;

class Polygon2Csg2dConverter {
    private final Polygon polygon;

    Polygon2Csg2dConverter(final Polygon polygon) {
        if (area(polygon) < 0) {
            this.polygon = polygon.negate();
        } else {
            this.polygon = new Polygon(polygon);
        }
    }

    /**
     * Signed area (-ve result means polygon goes anti-clockwise)
     * 
     * @return signed area
     */
    private static double area(final Polygon polygon) {
        double a = 0;
        for (int i = 1; i < polygon.size() - 1; i++) {
            final int j = i + 1;
            final Point2D p = Point2D.sub(polygon.point(i), polygon.point(0));
            final Point2D q = Point2D.sub(polygon.point(j), polygon.point(0));
            a += Point2D.op(q, p);
        }
        return a * 0.5;
    }

    CSG2D getCsg2d() {
        final List<Integer> all = allPoints();
        final int[] flags = new int[polygon.size()];
        return toCSGRecursive(all, 0, true, flags);
    }

    /**
     * Compute the CSG representation of a (sub)list recursively
     */
    CSG2D toCSGRecursive(final List<Integer> a, int level, final boolean wrapAround, final int[] flags) {
        flagSet(level, a, flags);
        level++;
        final List<Integer> ch = convexHull(a);
        if (ch.size() < 3) {
            throw new RuntimeException("null convex hull: " + ch.size() + " points.");
        }

        flagSet(level, ch, flags);
        CSG2D hull;

        if (level % 2 == 1) {
            hull = CSG2D.universe();
        } else {
            hull = CSG2D.nothing();
        }

        // Set-theoretically combine all the real edges on the convex hull
        int i, oldi, flag, oldFlag, start;

        if (wrapAround) {
            oldi = a.size() - 1;
            start = 0;
        } else {
            oldi = 0;
            start = 1;
        }

        for (i = start; i < a.size(); i++) {
            oldFlag = flags[a.get(oldi).intValue()];
            flag = flags[a.get(i).intValue()];

            if (oldFlag == level && flag == level) {
                final HalfPlane hp = new HalfPlane(listPoint(oldi, a), listPoint(i, a));
                if (level % 2 == 1) {
                    hull = CSG2D.intersection(hull, new CSG2D(hp));
                } else {
                    hull = CSG2D.union(hull, new CSG2D(hp));
                }
            }

            oldi = i;
        }

        // Finally deal with the sections on polygons that form the hull that
        // are not themselves on the hull.
        List<Integer> section = polSection(a, level, flags);
        while (section != null) {
            if (level % 2 == 1) {
                hull = CSG2D.intersection(hull, toCSGRecursive(section, level, false, flags));
            } else {
                hull = CSG2D.union(hull, toCSGRecursive(section, level, false, flags));
            }
            section = polSection(a, level, flags);
        }

        return hull;
    }

    /**
     * Compute the convex hull of all the points in the list
     * 
     * @return list of point index pairs of the points on the hull
     */
    List<Integer> convexHull(final List<Integer> subList) {
        if (subList.size() < 3) {
            throw new RuntimeException("attempt to compute hull for " + subList.size() + " points!");
        }

        final List<Integer> inConsideration = new ArrayList<Integer>(subList);
        final List<Integer> result = createTriangle(inConsideration);

        while (inConsideration.size() > 0) {
            // for each side of the current result polygon, find the point that is farthest outside
            Point2D p = listPoint(result.size() - 1, result);
            for (int i = 0; i < result.size(); i++) {
                final Point2D q = listPoint(i, result);
                final int corner = findCorner(inConsideration, new HalfPlane(p, q));
                if (corner != -1) {
                    result.add(i, inConsideration.remove(corner));
                    i++; // fix index after adding the new point to result
                }
                p = q;
            }
            // Remove all points within the current hull from further consideration
            outsideHull(inConsideration, toCSGHull(result));
        }

        return result;
    }

    private List<Integer> createTriangle(final List<Integer> inConsideration) {
        final List<Integer> result = new ArrayList<Integer>();
        moveTopmostPoint(inConsideration, result);
        moveBottommostPoint(inConsideration, result);
        moveExtremalPoint(inConsideration, result);
        clockWise(result);
        outsideHull(inConsideration, toCSGHull(result));
        return result;
    }

    private int findCorner(final List<Integer> inConsideration, final HalfPlane hp) {
        double vMax = 0;
        int corner = -1;
        for (int testPoint = inConsideration.size() - 1; testPoint >= 0; testPoint--) {
            final double v = hp.value(listPoint(testPoint, inConsideration));
            if (v >= vMax) {
                vMax = v;
                corner = testPoint;
            }
        }
        return corner;
    }

    private void moveExtremalPoint(final List<Integer> inConsideration, final List<Integer> result) {
        final HalfPlane hp = new HalfPlane(listPoint(0, result), listPoint(1, result));
        int extremal = 0;
        double extremum = 0;
        for (int i = 0; i < inConsideration.size(); i++) {
            final double value = Math.abs(hp.value(listPoint(i, inConsideration)));
            if (value > extremum) {
                extremum = value;
                extremal = i;
            }
        }
        movePoint(inConsideration, result, extremal);
    }

    private void moveBottommostPoint(final List<Integer> inConsideration, final List<Integer> result) {
        int bottom = 0;
        double yMin = Double.MAX_VALUE;
        for (int i = 0; i < inConsideration.size(); i++) {
            final double y = listPoint(i, inConsideration).y();
            if (y < yMin) {
                yMin = y;
                bottom = i;
            }
        }
        movePoint(inConsideration, result, bottom);
    }

    private void moveTopmostPoint(final List<Integer> inConsideration, final List<Integer> result) {
        int top = 0;
        double yMax = Double.MIN_VALUE;
        for (int i = 0; i < inConsideration.size(); i++) {
            final double y = listPoint(i, inConsideration).y();
            if (y > yMax) {
                yMax = y;
                top = i;
            }
        }
        movePoint(inConsideration, result, top);
    }

    private static boolean movePoint(final List<Integer> inConsideration, final List<Integer> result, final int index) {
        return result.add(inConsideration.remove(index));
    }

    private List<Integer> allPoints() {
        final List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < polygon.size(); i++) {
            result.add(new Integer(i));
        }
        return result;
    }

    /**
     * Get the next whole section to consider from list a
     * 
     * @return the section (null for none left)
     */
    static List<Integer> polSection(final List<Integer> a, final int level, final int[] flags) {
        int flag, oldi;
        oldi = a.size() - 1;
        int oldFlag = flags[a.get(oldi).intValue()];

        int ptr = -1;
        for (int i = 0; i < a.size(); i++) {
            flag = flags[a.get(i).intValue()];

            if (flag < level && oldFlag >= level) {
                ptr = oldi;
                break;
            }
            oldi = i;
            oldFlag = flag;
        }

        if (ptr < 0) {
            return null;
        }

        final List<Integer> result = new ArrayList<Integer>();
        result.add(a.get(ptr));
        ptr++;
        if (ptr > a.size() - 1) {
            ptr = 0;
        }
        while (flags[a.get(ptr).intValue()] < level) {
            result.add(a.get(ptr));
            ptr++;
            if (ptr > a.size() - 1) {
                ptr = 0;
            }
        }

        result.add(a.get(ptr));

        return result;
    }

    /**
     * find a point from a list of polygon points
     */
    Point2D listPoint(final int i, final List<Integer> a) {
        return polygon.point((a.get(i)).intValue());
    }

    /**
     * Put the points on a triangle (list a) in the right order
     */
    private void clockWise(final List<Integer> a) {
        if (a.size() != 3) {
            throw new RuntimeException("cannot order clockWise a polygon that is not a triangle: " + a);
        }
        final Point2D q = Point2D.sub(listPoint(1, a), listPoint(0, a));
        final Point2D r = Point2D.sub(listPoint(2, a), listPoint(0, a));
        if (Point2D.op(q, r) > 0) {
            final Integer k = a.get(0);
            a.set(0, a.get(1));
            a.set(1, k);
        }
    }

    /**
     * Turn the list of hull points into a CSG convex polygon
     */
    private CSG2D toCSGHull(final List<Integer> hullPoints) {
        Point2D p, q;
        CSG2D hull = CSG2D.universe();
        p = listPoint(hullPoints.size() - 1, hullPoints);
        for (int i = 0; i < hullPoints.size(); i++) {
            q = listPoint(i, hullPoints);
            hull = CSG2D.intersection(hull, new CSG2D(new HalfPlane(p, q)));
            p = q;
        }

        return hull;
    }

    /**
     * Remove all the points in a list that are within or on the hull
     */
    private void outsideHull(final List<Integer> inConsideration, final CSG2D hull) {
        final double small = Math.sqrt(MathRoutines.TINY_VALUE);
        for (int i = inConsideration.size() - 1; i >= 0; i--) {
            final Point2D p = listPoint(i, inConsideration);
            if (hull.value(p) <= small) {
                inConsideration.remove(i);
            }
        }
    }

    /**
     * Set all the flag values in a list the same
     */
    static void flagSet(final int f, final List<Integer> a, final int[] flags) {
        for (int i = 0; i < a.size(); i++) {
            flags[(a.get(i)).intValue()] = f;
        }
    }
}
