package org.reprap.geometry.polygons;

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

    /**
     * Constructor takes two intersection points with an STL triangle edge.
     */
    public LineSegment(final Point2D p, final Point2D q) {
        a = p;
        b = q;
    }

    public Point2D getA() {
        return a;
    }

    public Point2D getB() {
        return b;
    }
}