package org.reprap.geometry.polyhedra;

import javax.vecmath.Point3d;

import org.reprap.geometry.polygons.Interval;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Rectangle;

/**
 * 3D bounding box
 * 
 * @author ensab
 */
public final class BoundingBox {
    private final Rectangle XYbox;
    private final Interval Zint;

    public BoundingBox(final Point3d p0) {
        Zint = new Interval(p0.z, p0.z);
        XYbox = new Rectangle(new Interval(p0.x, p0.x), new Interval(p0.y, p0.y));
    }

    public void expand(final Point3d p0) {
        Zint.expand(p0.z);
        XYbox.expand(new Point2D(p0.x, p0.y));
    }

    public void expand(final BoundingBox b) {
        Zint.expand(b.Zint);
        XYbox.expand(b.XYbox);
    }

    public Rectangle getXYbox() {
        return XYbox;
    }

    public Interval getZint() {
        return Zint;
    }
}