/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *   contains code from STLObject, which is
 *   Copyright (C) 2006 Adrian Bowyer & The University of Bath
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
package org.reprap.io.stl;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Hashtable;

import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.Shape3D;
import javax.vecmath.Point3d;

import org.reprap.geometry.polyhedra.CSG3D;
import org.reprap.geometry.polyhedra.STLFileContents;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.io.csg.CSGReader;

import com.sun.j3d.loaders.Scene;

public class StlFileLoader {

    public static STLFileContents loadSTLFileContents(final File location) {
        final CSGReader csgr = new CSGReader(location);
        CSG3D csgResult = null;
        if (csgr.csgAvailable()) {
            csgResult = csgr.csg();
        }

        final Scene scene;
        try {
            scene = new StlFile().load(location.getAbsolutePath());
        } catch (final FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        final BranchGroup bgResult = scene.getSceneGroup();
        bgResult.setCapability(Node.ALLOW_BOUNDS_READ);
        bgResult.setCapability(Group.ALLOW_CHILDREN_READ);
        bgResult.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);

        BoundingBox bbox = null;
        double volume = 0;
        // Recursively add its attribute
        final Hashtable<?, ?> namedObjects = scene.getNamedObjects();
        for (final Object tt : Collections.list(namedObjects.elements())) {
            if (tt instanceof Shape3D) {
                final Shape3D value = (Shape3D) tt;
                volume += s3dVolume(value);
                // it is necessary that we have the bounding box by reference here.
                bbox = (BoundingBox) value.getBounds();
                value.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
                final GeometryArray g = (GeometryArray) value.getGeometry();
                g.setCapability(GeometryArray.ALLOW_COORDINATE_WRITE);
            }
        }

        return new STLFileContents(location, bgResult, csgResult, volume, bbox);
    }

    /**
     * Compute the volume of a Shape3D
     */
    private static double s3dVolume(final Shape3D shape) {
        double total = 0;
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d a = new Point3d();
        final Point3d b = new Point3d();
        final Point3d c = new Point3d();
        if (g != null) {
            for (int i = 0; i < g.getVertexCount(); i += 3) {
                g.getCoordinate(i, a);
                g.getCoordinate(i + 1, b);
                g.getCoordinate(i + 2, c);
                total += STLObject.prismVolume(a, b, c);
            }
        }
        return Math.abs(total);
    }

}
