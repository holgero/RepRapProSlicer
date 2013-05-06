package org.reprap.geometry.polyhedra;

import org.reprap.geometry.polygons.Point2D;

/**
 * Line segment consisting of two points.
 * 
 * @author Adrian
 */
public final class LineSegment {
    /**
     * The ends of the line segment
     */
    private final Point2D a;
    private final Point2D b;
    private final String material;

    /**
     * Constructor takes two intersection points with an STL triangle edge.
     */
    public LineSegment(final Point2D p, final Point2D q, final String material) {
        a = p;
        b = q;
        this.material = material;
    }

    public Point2D getA() {
        return a;
    }

    public Point2D getB() {
        return b;
    }

    public String getMaterial() {
        return material;
    }
}