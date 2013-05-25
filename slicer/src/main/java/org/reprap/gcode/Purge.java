/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *  code extracted from org.reprap.geometry.LayerRules
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
package org.reprap.gcode;

import javax.media.j3d.Transform3D;
import javax.vecmath.Vector3d;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.PrintSetting;
import org.reprap.configuration.PrinterSetting;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polyhedra.STLFileContents;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.io.stl.StlFileLoader;

public final class Purge {
    /**
     * The point at which to purge extruders
     */
    private final Point2D purgePoint;
    /**
     * The length of the purge trail in mm
     */
    private final double purgeL = 25;
    private final boolean purgeXOriented;
    private final CurrentConfiguration currentConfiguration;

    public Purge(final CurrentConfiguration currentConfiguration) {
        this.currentConfiguration = currentConfiguration;
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        final PrinterSetting printerSetting = currentConfiguration.getPrinterSetting();
        purgePoint = new Point2D(printSetting.getDumpX(), printSetting.getDumpY());
        final double maximumXvalue = printerSetting.getBedSizeX();
        final double maximumYvalue = printerSetting.getBedSizeY();
        purgeXOriented = Math.abs(maximumYvalue / 2 - purgePoint.y()) > Math.abs(maximumXvalue / 2 - purgePoint.x());
    }

    public Point2D getPurgeEnd(final GCodeExtruder extruder, final boolean low, final int pass) {
        double a = purgeL * 0.5;
        if (low) {
            a = -a;
        }
        final double b = extruder.getExtrusionSize() * (4 - (extruder.getID() * 3 + pass));
        if (purgeXOriented) {
            return Point2D.add(purgePoint, new Point2D(a, b));
        } else {
            return Point2D.add(purgePoint, new Point2D(b, a));
        }
    }

    public Point2D getPurgeMiddle() {
        return purgePoint;
    }

    public boolean isPurgeXOriented() {
        return purgeXOriented;
    }

    public STLObject getShield(final double modelZMax) {
        final String shieldMaterial = currentConfiguration.getMaterials().get(0).getName();
        final STLFileContents stlFileContents = StlFileLoader.loadSTLFileContents(currentConfiguration.getPrintSetting()
                .getShieldStlFile());
        final STLObject shield = STLObject.createStlObjectFromFile(stlFileContents, shieldMaterial, currentConfiguration);
        final Vector3d shieldSize = shield.extent();
        shield.rScale(modelZMax / shieldSize.z, true);

        final double zOff = 0.5 * (modelZMax - shieldSize.z);
        double xOff = purgePoint.x();
        double yOff = purgePoint.y();
        if (!isPurgeXOriented()) {
            shield.translate(new Vector3d(-0.5 * shieldSize.x, -0.5 * shieldSize.y, 0));
            final Transform3D t3d1 = shield.getTransform();
            final Transform3D t3d2 = new Transform3D();
            t3d2.rotZ(0.5 * Math.PI);
            t3d1.mul(t3d2);
            shield.setTransform(t3d1);
            shield.translate(new Vector3d(yOff, -xOff, zOff));
        } else {
            xOff -= 0.5 * shieldSize.x;
            yOff -= shieldSize.y;
            shield.translate(new Vector3d(xOff, yOff, zOff));
        }
        return shield;
    }
}
