/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
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

public class AirMoveOptimizer {

    private final List<Polygon> polygons = new ArrayList<>();
    private Polygon candidate;
    private Point2D start;

    public AirMoveOptimizer() {
        start = new Point2D(0, 0);
    }

    public void setStart(final Point2D start) {
        this.start = start;
    }

    public Point2D getStart() {
        return start;
    }

    public PolygonList reduceAirMovement(final PolygonList list) {
        if (list.size() == 0) {
            return list;
        }
        copyPolygons(list);
        return reduceAirMovement();
    }

    private PolygonList reduceAirMovement() {
        final PolygonList result = new PolygonList();

        while (!polygons.isEmpty()) {
            getNextCandidate();
            result.add(candidate);
            updateStart();
        }

        return result;
    }

    private void updateStart() {
        if (!candidate.isClosed()) {
            start = candidate.point(candidate.size() - 1);
        }
    }

    private void copyPolygons(final PolygonList list) {
        polygons.clear();
        for (int i = 0; i < list.size(); i++) {
            polygons.add(list.polygon(i));
        }
    }

    private void getNextCandidate() {
        final double distanceToStart = findCandidate();
        if (candidate != null) {
            polygons.remove(candidate);
            if (distanceOfStartPoint(candidate) > distanceToStart) {
                candidate = candidate.negate();
            }
        }
    }

    private double findCandidate() {
        double distanceToStart = Double.MAX_VALUE;
        candidate = null;
        for (final Polygon polygon : polygons) {
            final double distance = getDistanceToStart(polygon);
            if (distance < distanceToStart) {
                distanceToStart = distance;
                candidate = polygon;
            }
        }
        return distanceToStart;
    }

    private double getDistanceToStart(final Polygon polygon) {
        final double distanceOfStartPoint = distanceOfStartPoint(polygon);
        if (polygon.isClosed()) {
            return distanceOfStartPoint;
        }
        final double distanceOfEndPoint = distanceOfEndPoint(polygon);
        return Math.min(distanceOfStartPoint, distanceOfEndPoint);
    }

    private double distanceOfEndPoint(final Polygon polygon) {
        return Point2D.dSquared(start, polygon.point(polygon.size() - 1));
    }

    private double distanceOfStartPoint(final Polygon polygon) {
        return Point2D.dSquared(start, polygon.point(0));
    }
}
