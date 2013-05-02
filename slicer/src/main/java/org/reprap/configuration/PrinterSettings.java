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
package org.reprap.configuration;

public class PrinterSettings {
    // dimensions
    private double bedSizeX; // mm 200
    private double bedSizeY; // mm 200
    private double maximumZ; // mm 100
    // travel speeds
    private double maximumFeedrateX; // mm/minute 15000
    private double maximumFeedrateY; // mm/minute 15000
    private double maximumFeedrateZ; // mm/minute 200
    // G-Code
    private boolean relativeDistanceE; // boolean true

    public double getBedSizeX() {
        return bedSizeX;
    }

    void setBedSizeX(final double bedSizeX) {
        this.bedSizeX = bedSizeX;
    }

    public double getBedSizeY() {
        return bedSizeY;
    }

    void setBedSizeY(final double bedSizeY) {
        this.bedSizeY = bedSizeY;
    }

    public double getMaximumZ() {
        return maximumZ;
    }

    void setMaximumZ(final double maximumZ) {
        this.maximumZ = maximumZ;
    }

    public boolean useRelativeDistanceE() {
        return relativeDistanceE;
    }

    void setRelativeDistanceE(final boolean relativeDistanceE) {
        this.relativeDistanceE = relativeDistanceE;
    }

    public double getMaximumFeedrateX() {
        return maximumFeedrateX;
    }

    void setMaximumFeedrateX(final double maximumFeedrateX) {
        this.maximumFeedrateX = maximumFeedrateX;
    }

    public double getMaximumFeedrateY() {
        return maximumFeedrateY;
    }

    void setMaximumFeedrateY(final double maximumFeedrateY) {
        this.maximumFeedrateY = maximumFeedrateY;
    }

    public double getMaximumFeedrateZ() {
        return maximumFeedrateZ;
    }

    void setMaximumFeedrateZ(final double maximumFeedrateZ) {
        this.maximumFeedrateZ = maximumFeedrateZ;
    }
}
