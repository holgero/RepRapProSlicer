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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;

public class PrinterSetting {
    private static final double MACHINE_RESOLUTION = 0.05; // mm

    @XmlAttribute
    @XmlID
    private String name; // text
    // dimensions
    @XmlElement
    private double bedSizeX; // mm 200
    @XmlElement
    private double bedSizeY; // mm 200
    @XmlElement
    private double maximumZ; // mm 100
    // visualization
    @XmlElement
    private String buildPlatformStl; // file (path relative to configuration file) 
    // travel speeds
    @XmlElement
    private double maximumFeedrateX; // mm/minute 15000
    @XmlElement
    private double maximumFeedrateY; // mm/minute 15000
    @XmlElement
    private double maximumFeedrateZ; // mm/minute 200
    // G-Code
    @XmlElement
    private boolean relativeDistanceE; // boolean true
    @XmlElement
    private String gCodePrologue; // text
    @XmlElement
    private String gCodeEpilogue; // text
    // Extruders
    @XmlElementWrapper
    @XmlElement(name = "extruderSetting")
    private ExtruderSetting[] extruderSettings;

    PrinterSetting() {
    }

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

    public List<ExtruderSetting> getExtruderSettings() {
        return Collections.unmodifiableList(Arrays.asList(extruderSettings));
    }

    void setExtruderSettings(final ExtruderSetting[] extruderSettings) {
        this.extruderSettings = extruderSettings;
    }

    public double getMachineResolution() {
        return MACHINE_RESOLUTION;
    }

    public File getBuildPlatformStl() {
        return new File(Configuration.getReprapDirectory(), buildPlatformStl);
    }

    void setBuildPlatformStl(final String buildPlatformStl) {
        this.buildPlatformStl = buildPlatformStl;
    }

    public String getName() {
        return name;
    }

    void setName(final String name) {
        this.name = name;
    }

    public String getGcodePrologue() {
        return gCodePrologue;
    }

    void setGcodePrologue(final String gCodePrologue) {
        this.gCodePrologue = gCodePrologue;
    }

    public String getGcodeEpilogue() {
        return gCodeEpilogue;
    }

    void setGcodeEpilogue(final String gCodeEpilogue) {
        this.gCodeEpilogue = gCodeEpilogue;
    }
}
