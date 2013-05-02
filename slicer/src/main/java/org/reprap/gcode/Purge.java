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

import org.reprap.configuration.Preferences;
import org.reprap.configuration.PrintSettings;
import org.reprap.configuration.PrinterSettings;
import org.reprap.geometry.polygons.Point2D;

public final class Purge {
    /**
     * The point at which to purge extruders
     */
    private final Point2D purgePoint;
    /**
     * The length of the purge trail in mm
     */
    private final double purgeL = 25;
    private final GCodePrinter printer;
    private final boolean purgeXOriented;

    public Purge(final GCodePrinter printer) {
        this.printer = printer;
        final Preferences preferences = Preferences.getInstance();
        final PrintSettings printSettings = preferences.getPrintSettings();
        final PrinterSettings printerSettings = preferences.getPrinterSettings();
        purgePoint = new Point2D(printSettings.getDumpX(), printSettings.getDumpY());
        final double maximumXvalue = printerSettings.getBedSizeX();
        final double maximumYvalue = printerSettings.getBedSizeY();
        purgeXOriented = Math.abs(maximumYvalue / 2 - purgePoint.y()) > Math.abs(maximumXvalue / 2 - purgePoint.x());
    }

    public Point2D getPurgeEnd(final int extruderNumber, final boolean low, final int pass) {
        double a = purgeL * 0.5;
        if (low) {
            a = -a;
        }
        final double b = printer.getExtruder().getExtrusionSize() * (4 - (extruderNumber * 3 + pass));
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
}
