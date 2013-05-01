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

public class PrintSettings {
    // layers
    private double layerHeight; // mm 0.24
    // perimeters
    private int verticalShells; // # 2
    private int horizontalShells; // # 3
    // infill
    private double fillDensity; // fraction 0.21
    private FillPattern pattern; // rectilinear
    // speeds
    private double perimeterSpeed; // fraction 0.9
    private double infillSpeed; // fraction 1.0
    // skirt
    // TODO make this a real skirt around the print, give it a width, a height and a distance from the print
    private boolean skirt; // boolean false

    public double getLayerHeight() {
        return layerHeight;
    }

    public void setLayerHeight(final double layerHeight) {
        this.layerHeight = layerHeight;
    }

    public int getVerticalShells() {
        return verticalShells;
    }

    public void setVerticalShells(final int verticalShells) {
        this.verticalShells = verticalShells;
    }

    public int getHorizontalShells() {
        return horizontalShells;
    }

    public void setHorizontalShells(final int horizontalShells) {
        this.horizontalShells = horizontalShells;
    }

    public double getFillDensity() {
        return fillDensity;
    }

    public void setFillDensity(final double fillDensity) {
        this.fillDensity = fillDensity;
    }

    public FillPattern getFillPattern() {
        return pattern;
    }

    public void setFillPattern(final FillPattern pattern) {
        this.pattern = pattern;
    }

    public double getPerimeterSpeed() {
        return perimeterSpeed;
    }

    public void setPerimeterSpeed(final double perimeterSpeed) {
        this.perimeterSpeed = perimeterSpeed;
    }

    public double getInfillSpeed() {
        return infillSpeed;
    }

    public void setInfillSpeed(final double infillSpeed) {
        this.infillSpeed = infillSpeed;
    }

    public boolean isSkirt() {
        return skirt;
    }

    public void setSkirt(final boolean skirt) {
        this.skirt = skirt;
    }
}
