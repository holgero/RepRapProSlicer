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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.reprap.configuration.store.MathRoutines;

class Polygon2Csg2dConverter {
    private final Polygon polygon;

    Polygon2Csg2dConverter(final Polygon polygon) {
        if (area(polygon) < 0) {
            this.polygon = polygon.negate();
        } else {
            this.polygon = polygon;
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
        return toCSGRecursive(allPoints(), 0, true);
    }

    /**
     * Compute the CSG representation of a (sub)list recursively
     */
    private static CSG2D toCSGRecursive(final List<Point2D> a, int level, final boolean wrapAround) {
        level++;
        final Set<Point2D> hullSet = new HashSet<>(convexHull(a));
        if (hullSet.size() < 3) {
            throw new RuntimeException("invalid convex hull with " + hullSet.size() + " points.");
        }

        CSG2D hull;
        if (level % 2 == 1) {
            hull = CSG2D.universe();
        } else {
            hull = CSG2D.nothing();
        }

        // Set-theoretically combine all the real edges on the convex hull
        int oldi, start;
        if (wrapAround) {
            oldi = a.size() - 1;
            start = 0;
        } else {
            oldi = 0;
            start = 1;
        }

        for (int i = start; i < a.size(); i++) {
            if (hullSet.contains(a.get(oldi)) && hullSet.contains(a.get(i))) {
                final HalfPlane hp = new HalfPlane(a.get(oldi), a.get(i));
                if (level % 2 == 1) {
                    hull = CSG2D.intersection(hull, new CSG2D(hp));
                } else {
                    hull = CSG2D.union(hull, new CSG2D(hp));
                }
            }
            oldi = i;
        }

        final Set<Point2D> doneWith = new HashSet<>(hullSet);

        // Finally deal with the sections on polygons that form the hull that
        // are not themselves on the hull.
        while (a.size() > doneWith.size()) {
            final List<Point2D> section = findIndentation(a, doneWith);
            if (level % 2 == 1) {
                hull = CSG2D.intersection(hull, toCSGRecursive(section, level, false));
            } else {
                hull = CSG2D.union(hull, toCSGRecursive(section, level, false));
            }
            doneWith.addAll(section);
        }

        return hull;
    }

    /**
     * Compute the convex hull of all the points in the list
     */
    private static List<Point2D> convexHull(final List<Point2D> subList) {
        if (subList.size() < 3) {
            throw new RuntimeException("attempt to compute hull for " + subList.size() + " points!");
        }

        final List<Point2D> inConsideration = new ArrayList<Point2D>(subList);
        final List<Point2D> result = createTriangle(inConsideration);

        while (inConsideration.size() > 0) {
            // for each side of the current result polygon, find the point that is farthest outside
            Point2D p = result.get(result.size() - 1);
            for (int i = 0; i < result.size(); i++) {
                final Point2D q = result.get(i);
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

    private static List<Point2D> createTriangle(final List<Point2D> inConsideration) {
        final List<Point2D> result = new ArrayList<Point2D>();
        moveTopmostPoint(inConsideration, result);
        moveBottommostPoint(inConsideration, result);
        moveExtremalPoint(inConsideration, result);
        clockWise(result);
        outsideHull(inConsideration, toCSGHull(result));
        return result;
    }

    private static int findCorner(final List<Point2D> inConsideration, final HalfPlane hp) {
        double vMax = 0;
        int corner = -1;
        for (int testPoint = inConsideration.size() - 1; testPoint >= 0; testPoint--) {
            final double v = hp.value(inConsideration.get(testPoint));
            if (v >= vMax) {
                vMax = v;
                corner = testPoint;
            }
        }
        return corner;
    }

    private static void moveExtremalPoint(final List<Point2D> inConsideration, final List<Point2D> result) {
        final HalfPlane hp = new HalfPlane(result.get(0), result.get(1));
        int extremal = 0;
        double extremum = 0;
        for (int i = 0; i < inConsideration.size(); i++) {
            final double value = Math.abs(hp.value(inConsideration.get(i)));
            if (value > extremum) {
                extremum = value;
                extremal = i;
            }
        }
        movePoint(inConsideration, result, extremal);
    }

    private static void moveBottommostPoint(final List<Point2D> inConsideration, final List<Point2D> result) {
        int bottom = 0;
        double yMin = Double.MAX_VALUE;
        for (int i = 0; i < inConsideration.size(); i++) {
            final double y = inConsideration.get(i).y();
            if (y < yMin) {
                yMin = y;
                bottom = i;
            }
        }
        movePoint(inConsideration, result, bottom);
    }

    private static void moveTopmostPoint(final List<Point2D> inConsideration, final List<Point2D> result) {
        int top = 0;
        double yMax = Double.MIN_VALUE;
        for (int i = 0; i < inConsideration.size(); i++) {
            final double y = inConsideration.get(i).y();
            if (y > yMax) {
                yMax = y;
                top = i;
            }
        }
        movePoint(inConsideration, result, top);
    }

    private static boolean movePoint(final List<Point2D> inConsideration, final List<Point2D> result, final int index) {
        return result.add(inConsideration.remove(index));
    }

    private List<Point2D> allPoints() {
        final List<Point2D> result = new ArrayList<Point2D>();
        for (int i = 0; i < polygon.size(); i++) {
            result.add(polygon.point(i));
        }
        return result;
    }

    /**
     * Find a indentation in the hull. That is a section of points that are not
     * on the hull between two points on the hull. The result starts with a
     * point on the hull, then follow one to many points not on the hull. At
     * last comes a point on the hull again.
     */
    private static List<Point2D> findIndentation(final List<Point2D> points, final Set<Point2D> hullSet) {
        if (points.size() <= hullSet.size()) {
            throw new IllegalArgumentException("list of points to search must be bigger than the set of points on the hull");
        }
        if (hullSet.isEmpty()) {
            throw new IllegalArgumentException("set of points on the hull cannot be empty");
        }
        final List<Point2D> result = new ArrayList<Point2D>();
        int ptr = findFirstPointNotOnTheHull(points, hullSet);
        result.add(wrapAroundGet(points, ptr - 1));
        while (!hullSet.contains(wrapAroundGet(points, ptr))) {
            result.add(wrapAroundGet(points, ptr));
            ptr++;
        }
        result.add(wrapAroundGet(points, ptr));
        return result;
    }

    private static int findFirstPointNotOnTheHull(final List<Point2D> points, final Set<Point2D> hullSet) {
        int idx = findFirstPointOnTheHull(points, hullSet);
        // no need to check for boundaries: points list is bigger than the hullSet
        while (hullSet.contains(wrapAroundGet(points, idx))) {
            idx++;
        }
        return idx;
    }

    private static int findFirstPointOnTheHull(final List<Point2D> points, final Set<Point2D> hullSet) {
        int idx = 0;
        // no need to check for boundaries: hullSet is included in the points list and is not empty
        while (!hullSet.contains(wrapAroundGet(points, idx))) {
            idx++;
        }
        return idx;
    }

    private static Point2D wrapAroundGet(final List<Point2D> a, final int ptr) {
        final int size = a.size();
        return a.get((ptr + size) % size);
    }

    /**
     * Put the points on a triangle (list a) in the right order
     */
    private static void clockWise(final List<Point2D> a) {
        if (a.size() != 3) {
            throw new RuntimeException("cannot order clockWise a polygon that is not a triangle: " + a);
        }
        final Point2D q = Point2D.sub(a.get(1), a.get(0));
        final Point2D r = Point2D.sub(a.get(2), a.get(0));
        if (Point2D.op(q, r) > 0) {
            final Point2D k = a.get(0);
            a.set(0, a.get(1));
            a.set(1, k);
        }
    }

    /**
     * Turn the list of hull points into a CSG convex polygon
     */
    private static CSG2D toCSGHull(final List<Point2D> hullPoints) {
        Point2D p, q;
        CSG2D hull = CSG2D.universe();
        p = hullPoints.get(hullPoints.size() - 1);
        for (int i = 0; i < hullPoints.size(); i++) {
            q = hullPoints.get(i);
            hull = CSG2D.intersection(hull, new CSG2D(new HalfPlane(p, q)));
            p = q;
        }

        return hull;
    }

    /**
     * Remove all the points in a list that are within or on the hull
     */
    private static void outsideHull(final List<Point2D> inConsideration, final CSG2D hull) {
        final double small = Math.sqrt(MathRoutines.TINY_VALUE);
        for (int i = inConsideration.size() - 1; i >= 0; i--) {
            final Point2D p = inConsideration.get(i);
            if (hull.value(p) <= small) {
                inConsideration.remove(i);
            }
        }
    }
}
