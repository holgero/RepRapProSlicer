/*
 
 RepRap
 ------
 
 The Replicating Rapid Prototyper Project
 
 
 Copyright (C) 2005
 Adrian Bowyer & The University of Bath
 
 http://reprap.org
 
 Principal author:
 
 Adrian Bowyer
 Department of Mechanical Engineering
 Faculty of Engineering and Design
 University of Bath
 Bath BA2 7AY
 U.K.
 
 e-mail: A.Bowyer@bath.ac.uk
 
 RepRap is free; you can redistribute it and/or
 modify it under the terms of the GNU Library General Public
 Licence as published by the Free Software Foundation; either
 version 2 of the Licence, or (at your option) any later version.
 
 RepRap is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Library General Public Licence for more details.
 
 For this purpose the words "software" and "library" in the GNU Library
 General Public Licence are taken to mean any and all computer programs
 computer files data results documents and other copyright information
 available from the RepRap project.
 
 You should have received a copy of the GNU Library General Public
 Licence along with RepRap; if not, write to the Free
 Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA,
 or see
 
 http://www.gnu.org/
 
 =====================================================================
 
 
 RrPolygon: 2D polygons
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 A polygon is an auto-extending list of Rr2Points.  Its end is 
 sometimes considered to join back to its beginning, depending
 on context.
 
 It also keeps its enclosing box.  
 
 Each point is stored with a flag value.  This can be used to flag the
 point as visited, or to indicate if the subsequent line segment is to
 be plotted etc.
 
 java.awt.Polygon is no use for this because it has integer coordinates.
 
 */

package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.reprap.attributes.Attributes;
import org.reprap.attributes.Constants;
import org.reprap.debug.Debug;

/**
 * The main boundary-representation polygon class
 */
public class Polygon {
    /**
     * End joined to beginning?
     */
    private boolean closed = false;

    /**
     * Used to choose the starting point for a randomized-start copy of a
     * polygon
     */
    private static final Random RANDOM = new Random(918273);

    /**
     * The (X, Y) points round the polygon as Rr2Points
     */
    private final List<Point2D> points = new ArrayList<Point2D>();

    /**
     * The atributes of the STL object that this polygon represents
     */
    private final Attributes attributes;

    /**
     * The minimum enclosing X-Y box round the polygon
     */
    private Rectangle box = new Rectangle();

    /**
     * Make an empty polygon
     */
    public Polygon(final Attributes attributes, final boolean closed) {
        if (attributes == null) {
            throw new IllegalArgumentException("Polygon(): attributes must not be null");
        }
        this.attributes = attributes;
        this.closed = closed;
    }

    /**
     * Deep copy - NB: Attributes _not_ deep copied.
     */
    Polygon(final Polygon p) {
        this(p.attributes, p.closed);
        for (int i = 0; i < p.size(); i++) {
            add(new Point2D(p.point(i)));
        }
        closed = p.closed;
    }

    /**
     * Set the polygon not closed
     */
    void setOpen() {
        closed = false;
    }

    /**
     * Get the data
     * 
     * @param i
     * @return i-th point object of polygon
     */
    public Point2D point(final int i) {
        return points.get(i);
    }

    @Override
    public String toString() {
        String result = " Polygon -  vertices: ";
        result += size() + ", enclosing box: ";
        result += box.toString();
        result += "\n";
        for (int i = 0; i < size(); i++) {
            result += point(i).toString();
            result += "; ";
        }

        return result;
    }

    /**
     * Do we loop back on ourself?
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * @return number of points in polygon
     */
    public int size() {
        return points.size();
    }

    /**
     * Add a new point to the polygon
     */
    public void add(final Point2D p) {
        points.add(new Point2D(p));
        box.expand(p);
    }

    /**
     * Insert a new point into the polygon
     */
    public void add(final int i, final Point2D p) {
        points.add(i, new Point2D(p));
        box.expand(p);
    }

    /**
     * Set a point to be p
     */
    public void set(final int i, final Point2D p) {
        points.set(i, new Point2D(p));
        box.expand(p); // Note if the old point was on the convex hull, and the new one is within, box will be too big after this

    }

    public Attributes getAttributes() {
        return attributes;
    }

    /**
     * @return the current surrounding box
     */
    public Rectangle getBox() {
        return box;
    }

    /**
     * Sum of the edge lengths
     */
    double getLength() {
        double len = 0;
        for (int i = 1; i < size(); i++) {
            len = len + Point2D.d(point(i), point(i - 1));
        }
        if (closed) {
            len = len + Point2D.d(point(0), point(size() - 1));
        }
        return len;
    }

    /**
     * Put a new polygon on the end (N.B. Attributes of the new polygon are
     * ignored)
     */
    void add(final Polygon p) {
        if (p.size() == 0) {
            return;
        }
        for (int i = 0; i < p.size(); i++) {
            points.add(new Point2D(p.point(i)));
        }

        box.expand(p.box);
    }

    /**
     * Put a new polygon in the middle (at vertex k, which will be at the end of
     * the inserted polygon afterwards). (N.B. Attributes of the new polygon are
     * ignored)
     */
    private void add(int k, final Polygon p) {
        if (p.size() == 0) {
            return;
        }
        for (int i = 0; i < p.size(); i++) {
            points.add(k, new Point2D(p.point(i)));
            k++;
        }
        box.expand(p.box);
    }

    /**
     * Recompute the box (sometimes useful if points have been deleted)
     */
    private void re_box() {
        box = new Rectangle();
        final int leng = size();
        for (int i = 0; i < leng; i++) {
            box.expand(points.get(i));
        }
    }

    /**
     * Negate (i.e. reverse cyclic order)
     * 
     * @return reversed polygon object
     */
    Polygon negate() {
        final Polygon result = new Polygon(attributes, closed);
        for (int i = size() - 1; i >= 0; i--) {
            result.add(point(i));
        }
        return result;
    }

    /**
     * @return same polygon starting at a random vertex
     */
    public Polygon randomStart() {
        return newStart(RANDOM.nextInt(size()));
    }

    /**
     * @return same polygon, but starting at vertex i
     */
    public Polygon newStart(int i) {
        if (!isClosed()) {
            Debug.getInstance().errorMessage("RrPolygon.newStart(i): reordering an open polygon!");
        }

        if (i < 0 || i >= size()) {
            Debug.getInstance().errorMessage("RrPolygon.newStart(i): dud index: " + i);
            return this;
        }
        final Polygon result = new Polygon(attributes, closed);
        for (int j = 0; j < size(); j++) {
            result.add(point(i));
            i++;
            if (i >= size()) {
                i = 0;
            }
        }
        return result;
    }

    /**
     * Find the nearest vertex on a polygon to a given point
     */
    int nearestVertex(final Point2D p) {
        double d = Double.POSITIVE_INFINITY;
        int result = -1;
        for (int i = 0; i < size(); i++) {
            final double d2 = Point2D.dSquared(point(i), p);
            if (d2 < d) {
                d = d2;
                result = i;
            }
        }
        if (result < 0) {
            Debug.getInstance().errorMessage("RrPolygon.nearestVertex(): no point found!");
        }
        return result;
    }

    /**
     * Find the nearest vertex on this polygon to any on polygon p, reorder p so
     * that its nearest is its first one, then merge that polygon into this one.
     * The reordering is only done if the distance^2 is less than linkUp. If no
     * reordering and merging are done false is returned, otherwise true is
     * returned.
     */
    boolean nearestVertexReorderMerge(final Polygon p, final double linkUp) {
        if (!p.isClosed()) {
            Debug.getInstance().errorMessage("RrPolygon.nearestVertexReorder(): called for non-closed polygon.");
        }

        double d = Double.POSITIVE_INFINITY;
        int myPoint = -1;
        int itsPoint = -1;
        for (int i = 0; i < size(); i++) {
            final int j = p.nearestVertex(point(i));
            final double d2 = Point2D.dSquared(point(i), p.point(j));
            if (d2 < d) {
                d = d2;
                myPoint = i;
                itsPoint = j;
            }
        }
        if (itsPoint >= 0 && d < linkUp * linkUp) {
            final Polygon ro = p.newStart(itsPoint);
            ro.add(0, point(myPoint));
            add(myPoint, ro);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Find the index of the polygon point that has the maximal parametric
     * projection onto a line.
     */
    public int maximalVertex(final Line ln) {
        double d = Double.NEGATIVE_INFINITY;
        int result = -1;
        for (int i = 0; i < size(); i++) {
            final double d2 = ln.projection(point(i));
            if (d2 > d) {
                d = d2;
                result = i;
            }
        }
        if (result < 0) {
            Debug.getInstance().errorMessage("RrPolygon.maximalVertex(): no point found!");
        }
        return result;
    }

    /**
     * Signed area (-ve result means polygon goes anti-clockwise)
     * 
     * @return signed area
     */
    private double area() {
        double a = 0;
        Point2D p, q;
        int j;
        for (int i = 1; i < size() - 1; i++) {
            j = i + 1;
            p = Point2D.sub(point(i), point(0));
            q = Point2D.sub(point(j), point(0));
            a += Point2D.op(q, p);
        }
        return a * 0.5;
    }

    /**
     * @return the vertex at which the polygon deviates from a (nearly) straight
     *         line from v1
     */
    private int findAngleStart(final int v1, final double d2) {
        final int leng = size();
        final Point2D p1 = point(v1 % leng);
        int v2 = v1;
        for (int i = 0; i <= leng; i++) {
            v2++;
            final Line line = new Line(p1, point(v2 % leng));
            for (int j = v1 + 1; j < v2; j++) {
                if (line.d_2(point(j % leng)).x() > d2) {
                    return v2 - 1;
                }
            }
        }
        Debug.getInstance().debugMessage("RrPolygon.findAngleStart(): polygon is all one straight line!");
        return -1;
    }

    /**
     * Simplify a polygon by deleting points from it that are closer than d to
     * lines joining other points NB - this ignores speeds
     * 
     * @return simplified polygon object
     */
    Polygon simplify(final double d) {
        final int leng = size();
        if (leng <= 3) {
            return new Polygon(this);
        }
        final Polygon r = new Polygon(attributes, closed);
        final double d2 = d * d;

        final int v1 = findAngleStart(0, d2);
        // We get back -1 if the points are in a straight line.
        if (v1 < 0) {
            r.add(point(0));
            r.add(point(leng - 1));
            return r;
        }

        if (!isClosed()) {
            r.add(point(0));
        }

        r.add(point(v1 % leng));
        int v2 = v1;
        while (true) {
            // We get back -1 if the points are in a straight line. 
            v2 = findAngleStart(v2, d2);
            if (v2 < 0) {
                Debug.getInstance().errorMessage("RrPolygon.simplify(): points were not in a straight line; now they are!");
                return (r);
            }

            if (v2 > leng || (!isClosed() && v2 == leng)) {
                return (r);
            }

            if (v2 == leng && isClosed()) {
                r.points.add(0, point(0));
                r.re_box();
                return r;
            }
            r.add(point(v2 % leng));
        }
    }

    /**
     * find a point from a list of polygon points
     */
    private Point2D listPoint(final int i, final List<Integer> a) {
        return point((a.get(i)).intValue());
    }

    /**
     * find the top (+y) point of a polygon point list
     * 
     * @return the index in the list of the point
     */
    private int topPoint(final List<Integer> a) {
        int top = 0;
        double yMax = listPoint(top, a).y();
        double y;

        for (int i = 1; i < a.size(); i++) {
            y = listPoint(i, a).y();
            if (y > yMax) {
                yMax = y;
                top = i;
            }
        }

        return top;
    }

    /**
     * find the bottom (-y) point of a polygon point list
     * 
     * @return the index in the list of the point
     */
    private int bottomPoint(final List<Integer> a) {
        int bot = 0;
        double yMin = listPoint(bot, a).y();
        double y;

        for (int i = 1; i < a.size(); i++) {
            y = listPoint(i, a).y();
            if (y < yMin) {
                yMin = y;
                bot = i;
            }
        }

        return bot;
    }

    /**
     * Put the points on a triangle (list a) in the right order
     */
    private void clockWise(final List<Integer> a) {
        if (a.size() == 3) {
            final Point2D q = Point2D.sub(listPoint(1, a), listPoint(0, a));
            final Point2D r = Point2D.sub(listPoint(2, a), listPoint(0, a));
            if (Point2D.op(q, r) > 0) {
                final Integer k = a.get(0);
                a.set(0, a.get(1));
                a.set(1, k);
            }
        } else {
            Debug.getInstance().errorMessage("clockWise(): not called for a triangle!");
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
        final double small = Math.sqrt(Constants.TINY_VALUE);
        for (int i = inConsideration.size() - 1; i >= 0; i--) {
            final Point2D p = listPoint(i, inConsideration);
            if (hull.value(p) <= small) {
                inConsideration.remove(i);
            }
        }
    }

    /**
     * Compute the convex hull of all the points in the list
     * 
     * @return list of point index pairs of the points on the hull
     */
    private List<Integer> convexHull(final List<Integer> subList) {
        if (subList.size() < 3) {
            Debug.getInstance().errorMessage("convexHull(): attempt to compute hull for " + subList.size() + " points!");
            return new ArrayList<Integer>();
        }

        final List<Integer> inConsideration = new ArrayList<Integer>(subList);

        int i;

        // The top-most and bottom-most points must be on the hull
        final List<Integer> result = new ArrayList<Integer>();
        final int t = topPoint(inConsideration);
        final int b = bottomPoint(inConsideration);
        result.add(inConsideration.get(t));
        result.add(inConsideration.get(b));
        if (t > b) {
            inConsideration.remove(t);
            inConsideration.remove(b);
        } else {
            inConsideration.remove(b);
            inConsideration.remove(t);
        }

        // Repeatedly add the point that's farthest outside the current hull
        int corner, after;
        CSG2D hull;
        double v, vMax;
        Point2D p, q;
        HalfPlane hp;
        while (inConsideration.size() > 0) {
            vMax = 0; // Need epsilon?
            corner = -1;
            after = -1;
            for (int testPoint = inConsideration.size() - 1; testPoint >= 0; testPoint--) {
                p = listPoint(result.size() - 1, result);
                for (i = 0; i < result.size(); i++) {
                    q = listPoint(i, result);
                    hp = new HalfPlane(p, q);
                    v = hp.value(listPoint(testPoint, inConsideration));
                    if (result.size() == 2) {
                        v = Math.abs(v);
                    }
                    if (v >= vMax) {
                        after = i;
                        vMax = v;
                        corner = testPoint;
                    }
                    p = q;
                }
            }

            if (corner >= 0) {
                result.add(after, inConsideration.get(corner));
                inConsideration.remove(corner);
            } else if (inConsideration.size() > 0) {
                Debug.getInstance().errorMessage("convexHull(): points left, but none included!");
                return result;
            }

            // Get the first triangle in the right order
            if (result.size() == 3) {
                clockWise(result);
            }

            // Remove all points within the current hull from further consideration
            hull = toCSGHull(result);
            outsideHull(inConsideration, hull);
        }

        return result;
    }

    /**
     * @return list of indices of points in the polygons
     */
    private List<Integer> allPoints() {
        final List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < size(); i++) {
            result.add(new Integer(i));
        }
        return result;
    }

    /**
     * Set all the flag values in a list the same
     */
    private void flagSet(final int f, final List<Integer> a, final int[] flags) {
        for (int i = 0; i < a.size(); i++) {
            flags[(a.get(i)).intValue()] = f;
        }
    }

    /**
     * Get the next whole section to consider from list a
     * 
     * @return the section (null for none left)
     */
    private List<Integer> polSection(final List<Integer> a, final int level, final int[] flags) {
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
     * Compute the CSG representation of a (sub)list recursively
     */
    private CSG2D toCSGRecursive(final List<Integer> a, int level, final boolean wrapAround, final int[] flags) {
        flagSet(level, a, flags);
        level++;
        final List<Integer> ch = convexHull(a);
        if (ch.size() < 3) {
            Debug.getInstance().errorMessage("toCSGRecursive() - null convex hull: " + ch.size() + " points.");
            return CSG2D.nothing();
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
     * Convert a polygon to CSG representation
     * 
     * @return CSG polygon object based on polygon and tolerance
     */
    CSG2D toCSG() {
        Polygon copy = new Polygon(this);
        if (copy.area() < 0) {
            copy = copy.negate();
        }

        final List<Integer> all = copy.allPoints();
        final int[] flags = new int[copy.size()];
        final CSG2D expression = copy.toCSGRecursive(all, 0, true, flags);

        return expression;
    }
}
