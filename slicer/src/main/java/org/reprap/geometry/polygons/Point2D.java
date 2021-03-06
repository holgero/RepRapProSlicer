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
 
 
 Rr2Point: 2D vectors
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 
 */

package org.reprap.geometry.polygons;

/**
 * Class for (x, y) points and vectors
 */
public class Point2D {
    private double x;
    private double y;

    public Point2D(final double a, final double b) {
        x = a;
        y = b;
    }

    Point2D(final Point2D r) {
        this(r.x, r.y);
    }

    @Override
    public String toString() {
        return Double.toString(x) + " " + Double.toString(y);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    Point2D neg() {
        return new Point2D(-x, -y);
    }

    Point2D orthogonal() {
        return new Point2D(y, -x);
    }

    /**
     * @return a new point based on a vector addition of points a and b
     */
    public static Point2D add(final Point2D a, final Point2D b) {
        final Point2D r = new Point2D(a);
        r.x += b.x;
        r.y += b.y;
        return r;
    }

    /**
     * @return a new point based on a vector subtraction of a - b
     */
    public static Point2D sub(final Point2D a, final Point2D b) {
        return add(a, b.neg());
    }

    public static Point2D mul(final Point2D b, final double factor) {
        return new Point2D(b.x * factor, b.y * factor);
    }

    /**
     * Downscale a point
     * 
     * @param b
     *            An R2rPoint
     * @param factor
     *            A scale factor
     * @return The point Rr2Point divided by a factor of a
     */
    public static Point2D div(final Point2D b, final double factor) {
        return mul(b, 1 / factor);
    }

    /**
     * Inner product
     * 
     * @return The scalar product of the points
     */
    public static double mul(final Point2D a, final Point2D b) {
        return a.x * b.x + a.y * b.y;
    }

    /**
     * Modulus
     * 
     * @return modulus
     */
    public double mod() {
        return Math.sqrt(mul(this, this));
    }

    /**
     * Unit length normalization
     * 
     * @return normalized unit length
     */
    public Point2D norm() {
        return div(this, mod());
    }

    /**
     * Outer product
     * 
     * @return outer product
     */
    static double op(final Point2D a, final Point2D b) {
        return a.x * b.y - a.y * b.x;
    }

    /**
     * Squared distance
     * 
     * @return squared distance
     */
    public static double dSquared(final Point2D a, final Point2D b) {
        final Point2D c = sub(a, b);
        return mul(c, c);
    }

    /**
     * distance
     * 
     * @return distance
     */
    public static double d(final Point2D a, final Point2D b) {
        return Math.sqrt(dSquared(a, b));
    }
}
