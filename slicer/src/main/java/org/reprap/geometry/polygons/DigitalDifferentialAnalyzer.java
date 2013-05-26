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
 * A digital differential analyzer (DDA), calculates the points of a straight
 * line.
 * 
 * @author ensab
 */
final class DigitalDifferentialAnalyzer {
    private static final class CoordinateState {
        final int delta;
        final boolean positive;
        int count;
        int next;

        CoordinateState(final int delta, final boolean positive, final int count, final int next) {
            this.delta = delta;
            this.positive = positive;
            this.count = count;
            this.next = next;
        }

        void advance(final int steps) {
            count += delta;
            if (count > 0) {
                count -= steps;
                if (positive) {
                    next++;
                } else {
                    next--;
                }
            }
        }
    }

    private final CoordinateState stateX;
    private final CoordinateState stateY;
    private final int steps;
    private boolean finished = false;
    private int taken = 0;

    /**
     * Set up the DDA between a start and an end point
     */
    DigitalDifferentialAnalyzer(final Integer2DPoint start, final Integer2DPoint end) {
        final Integer2DPoint delta = end.sub(start).abs();
        steps = Math.max(delta.getX(), delta.getY());
        stateX = new CoordinateState(delta.getX(), end.getX() >= start.getX(), -steps / 2, start.getX());
        stateY = new CoordinateState(delta.getY(), end.getY() >= start.getY(), -steps / 2, start.getY());
    }

    /**
     * Return the next point along the line, or null if the last point returned
     * was the final one.
     */
    Integer2DPoint next() {
        if (finished) {
            return null;
        }

        final Integer2DPoint result = new Integer2DPoint(stateX.next, stateY.next);
        finished = taken >= steps;
        taken++;
        stateX.advance(steps);
        stateY.advance(steps);
        return result;
    }
}