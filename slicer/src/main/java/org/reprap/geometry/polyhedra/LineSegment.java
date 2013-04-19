package org.reprap.geometry.polyhedra;

import org.reprap.attributes.Attributes;
import org.reprap.geometry.polygons.Point2D;

/**
 * Line segment consisting of two points.
 * 
 * @author Adrian
 */
final class LineSegment {
    /**
     * The ends of the line segment
     */
    private final Point2D a;
    private final Point2D b;

    /**
     * The attribute describes the material of the segment.
     */
    private final Attributes attribute;

    /**
     * Constructor takes two intersection points with an STL triangle edge.
     */
    LineSegment(final Point2D p, final Point2D q, final Attributes at) {
        a = p;
        b = q;
        attribute = at;
    }

    Point2D getA() {
        return a;
    }

    Point2D getB() {
        return b;
    }

    Attributes getAttribute() {
        return attribute;
    }
}