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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

public class ExtruderSetting {
    @XmlElement
    private double nozzleDiameter; // mm 0.5
    @XmlElement
    private double extrudeRatio; // ratio 1.0
    @XmlElement
    private double airExtrusionFeedRate; // mm/minute 40000.0
    @XmlElement
    private double printExtrusionRate; // mm/minute 2000.0
    @XmlElement
    private double retraction; // mm 2.079
    @XmlElement
    private double extraLengthPerLayer; // mm 0.104
    @XmlElement
    private double extraLengthPerPolygon; // mm 0.104
    @XmlElement
    private double extrusionOverrun; // # -1
    @XmlElement
    private double lift; // mm 0
    @XmlElement
    @XmlIDREF
    private MaterialSetting material;

    public double getNozzleDiameter() {
        return nozzleDiameter;
    }

    void setNozzleDiameter(final double nozzleDiameter) {
        this.nozzleDiameter = nozzleDiameter;
    }

    public double getRetraction() {
        return retraction;
    }

    void setRetraction(final double retraction) {
        this.retraction = retraction;
    }

    public double getExtraLengthPerLayer() {
        return extraLengthPerLayer;
    }

    void setExtraLengthPerLayer(final double extraLengthPerLayer) {
        this.extraLengthPerLayer = extraLengthPerLayer;
    }

    public double getExtraLengthPerPolygon() {
        return extraLengthPerPolygon;
    }

    void setExtraLengthPerPolygon(final double extraLengthPerPolygon) {
        this.extraLengthPerPolygon = extraLengthPerPolygon;
    }

    public double getExtrudeRatio() {
        return extrudeRatio;
    }

    void setExtrudeRatio(final double extrudeRatio) {
        this.extrudeRatio = extrudeRatio;
    }

    public double getExtrusionOverrun() {
        return extrusionOverrun;
    }

    void setExtrusionOverrun(final double extrusionOverrun) {
        this.extrusionOverrun = extrusionOverrun;
    }

    public double getAirExtrusionFeedRate() {
        return airExtrusionFeedRate;
    }

    void setAirExtrusionFeedRate(final double airExtrusionFeedRate) {
        this.airExtrusionFeedRate = airExtrusionFeedRate;
    }

    public double getPrintExtrusionRate() {
        return printExtrusionRate;
    }

    void setPrintExtrusionRate(final double printExtrusionRate) {
        this.printExtrusionRate = printExtrusionRate;
    }

    public double getLift() {
        return lift;
    }

    void setLift(final double lift) {
        this.lift = lift;
    }

    public MaterialSetting getMaterial() {
        return material;
    }

    void setMaterial(final MaterialSetting material) {
        this.material = material;
    }

    public double getExtrusionSize() {
        return getNozzleDiameter();
    }
}
