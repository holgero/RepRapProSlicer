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
 * Holds rectangles represented by the sw point and the size.
 * 
 */
final class Integer2DRectangle {
    private final Integer2DPoint swCorner;
    private final int sizeX;
    private final int sizeY;

    /**
     * Construct from the corner points
     */
    Integer2DRectangle(final Integer2DPoint min, final Integer2DPoint max) {
        swCorner = new Integer2DPoint(min);
        sizeX = max.getX() - min.getX() + 1;
        sizeY = max.getY() - min.getY() + 1;
    }

    /**
     * Copy constructor
     */
    Integer2DRectangle(final Integer2DRectangle r) {
        this(r.swCorner, r.getNeCorner());
    }

    /**
     * Useful to have a single-pixel at the origin
     */
    Integer2DRectangle() {
        this(new Integer2DPoint(0, 0), new Integer2DPoint(0, 0));
    }

    /**
     * Create an Integer2DRectangle from a Rectangle in world coordinates.
     */
    Integer2DRectangle(final Rectangle rectangle, final double pixelSize) {
        this(toInternalPoint(rectangle.sw(), pixelSize), toInternalPoint(rectangle.ne(), pixelSize));
    }

    private static Integer2DPoint toInternalPoint(final Point2D realPoint, final double pixelSize) {
        final Point2D scaled = Point2D.mul(realPoint, 1 / pixelSize);
        return new Integer2DPoint((int) Math.round(scaled.x()), (int) Math.round(scaled.y()));
    }

    /**
     * Are two rectangles the same?
     */
    boolean coincidesWith(final Integer2DRectangle b) {
        return swCorner.coincidesWith(b.swCorner) && getSizeX() == b.getSizeX() && getSizeY() == b.getSizeY();
    }

    /**
     * This rectangle in the real world
     */
    Rectangle realRectangle(final double pixelSize) {
        return new Rectangle(realPoint(swCorner, pixelSize), realPoint(getNeCorner(), pixelSize));
    }

    /**
     * Big rectangle containing the union of two.
     */
    Integer2DRectangle union(final Integer2DRectangle b) {
        final int swX = Math.min(swCorner.getX(), b.swCorner.getX());
        final int swY = Math.min(swCorner.getY(), b.swCorner.getY());
        final int neX = Math.max(swX + getSizeX() - 1, b.swCorner.getX() + b.getSizeX() - 1);
        final int neY = Math.max(swY + getSizeY() - 1, b.swCorner.getY() + b.getSizeY() - 1);
        return new Integer2DRectangle(new Integer2DPoint(swX, swY), new Integer2DPoint(neX, neY));
    }

    /**
     * Rectangle containing the intersection of two.
     */
    Integer2DRectangle intersection(final Integer2DRectangle b) {
        final int swX = Math.max(swCorner.getX(), b.swCorner.getX());
        final int swY = Math.max(swCorner.getY(), b.swCorner.getY());
        final int neX = Math.min(swX + getSizeX() - 1, b.swCorner.getX() + b.getSizeX() - 1);
        final int neY = Math.min(swY + getSizeY() - 1, b.swCorner.getY() + b.getSizeY() - 1);
        return new Integer2DRectangle(new Integer2DPoint(swX, swY), new Integer2DPoint(neX, neY));
    }

    /**
     * Grow (dist +ve) or shrink (dist -ve).
     */
    Integer2DRectangle offset(final int dist) {
        final Integer2DPoint distance = new Integer2DPoint(dist, dist);
        return new Integer2DRectangle(swCorner.sub(distance), getNeCorner().add(new Integer2DPoint(dist, dist)));
    }

    /**
     * Anything there?
     */
    boolean isEmpty() {
        return getSizeX() < 0 | getSizeY() < 0;
    }

    Point2D realPoint(final Integer2DPoint point, final double pixelSize) {
        return new Point2D(scale(swCorner.getX() + point.getX(), pixelSize), scale(swCorner.getY() + point.getY(), pixelSize));
    }

    /**
     * Convert real-world point to integer relative to this rectangle
     */
    Integer2DPoint convertToInteger2DPoint(final Point2D a, final double pixelSize) {
        return new Integer2DPoint(iScale(a.x(), pixelSize) - swCorner.getX(), iScale(a.y(), pixelSize) - swCorner.getY());
    }

    /**
     * Convert from real to pixel coordinates
     */
    static int iScale(final double d, final double pixelSize) {
        return (int) Math.round(d / pixelSize);
    }

    /**
     * Convert integer coordinates to pixel coordinates
     */
    static double scale(final int i, final double pixelSize) {
        return i * pixelSize;
    }

    Integer2DPoint getSwCorner() {
        return swCorner;
    }

    private Integer2DPoint getNeCorner() {
        return new Integer2DPoint(swCorner.getX() + getSizeX() - 1, swCorner.getY() + getSizeY() - 1);
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }
}