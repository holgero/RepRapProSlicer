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
 
 RrHalfPlane: 2D planar half-spaces
 
 First version 20 May 2005
 This version: 9 March 2006
 
 */

package org.reprap.geometry.polygons;


/**
 * Class to hold and manipulate linear half-planes
 */
public class HalfPlane {
    /**
     * The half-plane is normal*(x, y) + offset <= 0
     */
    private Point2D normal = null;
    private double offset;

    /**
     * Keep the parametric equivalent to save computing it
     */
    private Line p = null;

    /**
     * Make one from two points on its edge
     */
    public HalfPlane(final Point2D a, final Point2D b) {
        p = new Line(a, b);
        p.norm();
        normal = new Point2D(-p.direction().y(), p.direction().x());
        offset = -Point2D.mul(p.origin(), normal());
    }

    /**
     * Deep copy
     */
    HalfPlane(final HalfPlane a) {
        normal = new Point2D(a.normal);
        offset = a.offset;
        p = new Line(a.p);
    }

    /**
     * Get the parametric equivalent
     * 
     * @return parametric equivalent of a line
     */
    public Line pLine() {
        return p;
    }

    @Override
    public String toString() {
        return "|" + normal.toString() + ", " + Double.toString(offset) + "|";
    }

    /**
     * Get the components
     */
    public Point2D normal() {
        return normal;
    }

    /**
     * Is another line the same within a tolerance?
     * 
     * @return 0 if the distance between halfplane a and b is less then the
     *         tolerance, -1 if one is the complement of the other within the
     *         tolerance, otherwise 1
     */
    static int same(final HalfPlane a, final HalfPlane b, final double tolerance) {
        if (a == b) {
            return 0;
        }

        int result = 0;
        if (Math.abs(a.normal.x() - b.normal.x()) > tolerance) {
            if (Math.abs(a.normal.x() + b.normal.x()) > tolerance) {
                return 1;
            }
            result = -1;
        }
        if (Math.abs(a.normal.y() - b.normal.y()) > tolerance) {
            if (Math.abs(a.normal.y() + b.normal.y()) > tolerance || result != -1) {
                return 1;
            }
        }
        final double rms = Math.sqrt((a.offset * a.offset + b.offset * b.offset) * 0.5);
        if (Math.abs(a.offset - b.offset) > tolerance * rms) {
            if (Math.abs(a.offset + b.offset) > tolerance * rms || result != -1) {
                return 1;
            }
        }

        return result;
    }

    /**
     * Change the sense
     * 
     * @return complement of half plane
     */
    public HalfPlane complement() {
        final HalfPlane r = new HalfPlane(this);
        r.normal = r.normal.neg();
        r.offset = -r.offset;
        r.p = r.p.neg();
        return r;
    }

    public HalfPlane offset(final double d) {
        final HalfPlane r = new HalfPlane(this);
        r.offset = r.offset - d;
        r.p = p.offset(d);
        return r;
    }

    /**
     * Find the potential value of a point
     * 
     * @return potential value of point p
     */
    public double value(final Point2D point) {
        return offset + Point2D.mul(normal, point);
    }

    /**
     * Find the potential interval of a box
     * 
     * @return potential interval of box b
     */
    Interval value(final Rectangle b) {
        return Interval.add(Interval.add((Interval.mul(b.x(), normal.x())), (Interval.mul(b.y(), normal.y()))), offset);
    }

    /**
     * The point where another line crosses
     * 
     * @return cross point
     * @throws ParallelException
     */
    public Point2D crossPoint(final HalfPlane a) throws ParallelException {
        double det = Point2D.op(normal, a.normal);
        if (det == 0) {
            throw new ParallelException("cross_point: parallel lines.");
        }
        det = 1 / det;
        final double x = normal.y() * a.offset - a.normal.y() * offset;
        final double y = a.normal.x() * offset - normal.x() * a.offset;
        return new Point2D(x * det, y * det);
    }

    /**
     * Take a range of parameter values and a line, and find the intersection of
     * that range with the part of the line (if any) on the solid side of the
     * half-plane.
     * 
     * @return intersection interval
     */
    public Interval wipe(final Line a, final Interval range) {
        if (range.empty()) {
            return range;
        }

        // Which way is the line pointing relative to our normal?
        final double product = Point2D.mul(a.direction(), normal);
        if (product == 0) {
            // parallel
            final double t = value(a.origin());
            if (t <= 0) {
                return range;
            } else {
                return new Interval();
            }
        }
        final boolean wipeDown = (product > 0);
        final double t = -value(a.origin()) / product;
        if (t >= range.high()) {
            if (wipeDown) {
                return range;
            } else {
                return new Interval();
            }
        } else if (t <= range.low()) {
            if (wipeDown) {
                return new Interval();
            } else {
                return range;
            }
        } else {
            if (wipeDown) {
                return new Interval(range.low(), t);
            } else {
                return new Interval(t, range.high());
            }
        }
    }
}
