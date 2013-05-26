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

/**
 * Integer 2D point
 * 
 * @author ensab
 */
final class Integer2DPoint {
    private final int x;
    private final int y;

    Integer2DPoint(final int xa, final int ya) {
        x = xa;
        y = ya;
    }

    /**
     * Copy constructor
     */
    Integer2DPoint(final Integer2DPoint a) {
        x = a.x;
        y = a.y;
    }

    /**
     * Are two points the same?
     */
    boolean coincidesWith(final Integer2DPoint b) {
        return x == b.x && y == b.y;
    }

    /**
     * Vector addition
     */
    Integer2DPoint add(final Integer2DPoint b) {
        return new Integer2DPoint(x + b.x, y + b.y);
    }

    /**
     * Vector subtraction
     */
    Integer2DPoint sub(final Integer2DPoint b) {
        return new Integer2DPoint(x - b.x, y - b.y);
    }

    /**
     * Opposite direction
     */
    Integer2DPoint neg() {
        return new Integer2DPoint(-x, -y);
    }

    /**
     * Absolute value
     */
    Integer2DPoint abs() {
        return new Integer2DPoint(Math.abs(x), Math.abs(y));
    }

    /**
     * Squared length
     */
    long magnitude2() {
        return x * x + y * y;
    }

    @Override
    public String toString() {
        return ": " + x + ", " + y + " :";
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }
}