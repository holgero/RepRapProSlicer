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
 modify it under the terms of the GNU Library General private
 Licence as published by the Free Software Foundation; either
 version 2 of the Licence, or (at your option) any later version.
 
 RepRap is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Library General private Licence for more details.
 
 For this purpose the words "software" and "library" in the GNU Library
 General private Licence are taken to mean any and all computer programs
 computer files data results documents and other copyright information
 available from the RepRap project.
 
 You should have received a copy of the GNU Library General private
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The main boundary-representation polygon class
 */
public class Polygon {
    private static final Logger LOGGER = LogManager.getLogger(Polygon.class);
    /**
     * End joined to beginning?
     */
    private boolean closed = false;

    /**
     * The (X, Y) points round the polygon as Rr2Points
     */
    private final List<Point2D> points = new ArrayList<Point2D>();

    private final String material;

    /**
     * The minimum enclosing X-Y box round the polygon
     */
    private Rectangle box = new Rectangle();

    /**
     * Make an empty polygon
     */
    public Polygon(final String material, final boolean closed) {
        if (material == null) {
            throw new IllegalArgumentException("Polygon(): material must not be null");
        }
        this.material = material;
        this.closed = closed;
    }

    /**
     * Deep copy - NB: Attributes _not_ deep copied.
     */
    Polygon(final Polygon p) {
        this(p.material, p.closed);
        for (int i = 0; i < p.size(); i++) {
            add(new Point2D(p.point(i)));
        }
        closed = p.closed;
    }

    public String getMaterial() {
        return material;
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

    /**
     * @return the current surrounding box
     */
    public Rectangle getBox() {
        return box;
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
        final Polygon result = new Polygon(material, closed);
        for (int i = size() - 1; i >= 0; i--) {
            result.add(point(i));
        }
        return result;
    }

    /**
     * @return same polygon, but starting at vertex i
     */
    Polygon newStart(int i) {
        if (!isClosed()) {
            throw new RuntimeException("attempt to reorder an open polygon");
        }

        if (i < 0 || i >= size()) {
            throw new ArrayIndexOutOfBoundsException("polygon size " + size() + ", invalid index: " + i);
        }
        final Polygon result = new Polygon(material, closed);
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
            throw new RuntimeException("found no point nearest to: " + p);
        }
        return result;
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
        LOGGER.debug("polygon is all one straight line!");
        return -1;
    }

    /**
     * Create a new simplified polygon by only points to it that are not closer
     * than d to lines joining other points.
     * 
     * @return simplified polygon object
     */
    Polygon simplify(final double d) {
        final int leng = size();
        if (leng <= 3) {
            return new Polygon(this);
        }
        final Polygon result = new Polygon(material, closed);
        final double d2 = d * d;

        final int v1 = findAngleStart(0, d2);
        // We get back -1 if the points are in a straight line.
        if (v1 < 0) {
            result.add(point(0));
            result.add(point(leng - 1));
            return result;
        }

        if (!isClosed()) {
            result.add(point(0));
        }

        result.add(point(v1 % leng));
        int v2 = v1;
        while (true) {
            // We get back -1 if the points are in a straight line. 
            v2 = findAngleStart(v2, d2);
            if (v2 < 0) {
                LOGGER.error("RrPolygon.simplify(): points were not in a straight line; now they are!");
                return result;
            }

            if (v2 > leng || (!isClosed() && v2 == leng)) {
                if (v2 % leng < v1) {
                    result.points.add(0, point(0));
                    result.re_box();
                }
                return result;
            }

            if (v2 == leng && isClosed()) {
                result.points.add(0, point(0));
                result.re_box();
                return result;
            }
            result.add(point(v2 % leng));
        }
    }

}
