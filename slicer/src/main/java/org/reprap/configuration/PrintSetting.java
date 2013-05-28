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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


public class PrintSetting implements NamedSetting {
    @XmlAttribute
    @XmlID
    private String name;
    // layers
    @XmlElement
    private double layerHeight; // mm 0.24
    // perimeters
    @XmlElement
    private int verticalShells; // # 2
    @XmlElement
    private int horizontalShells; // # 3
    @XmlElement
    private boolean insideOut; // boolean true
    @XmlElement
    private boolean middleStart; // boolean false
    @XmlElement
    private double arcCompensation; // factor 8.0
    @XmlElement
    private double arcShortSides; // mm 1.0
    // infill
    @XmlElement
    private double fillDensity; // fraction 0.21
    @XmlJavaTypeAdapter(FillPatternXmlAdapter.class)
    private FillPattern pattern; // rectilinear
    @XmlElement
    private double infillOverlap; // mm 0.2
    // speeds
    @XmlElement
    private double perimeterSpeed; // fraction 0.9
    @XmlElement
    private double infillSpeed; // fraction 1.0
    // skirt
    // TODO make this a real skirt around the print, give it a width, a height and a distance from the print
    @XmlElement
    private boolean skirt; // boolean false
    // brim
    @XmlElement
    private int brimLines; // # 8
    // dump area and shield
    @XmlElement
    private boolean shield; // boolean true
    @XmlElement
    private String shieldStlFile; // file (path relative to configuration file) 
    @XmlElement
    private int dumpX; // mm 50
    @XmlElement
    private int dumpY; // mm 100
    // support material
    @XmlElement
    private boolean support; // boolean false
    @XmlElement
    private int supportExtruder; // # 0
    @XmlJavaTypeAdapter(FillPatternXmlAdapter.class)
    private FillPattern supportPattern; // linear
    @XmlElement
    private double supportSpacing; // mm 2.5
    @XmlElement
    private int raftLayers; // # 0
    // output options
    @XmlElement
    private boolean verboseGCode; // boolean false
    @XmlElement
    private boolean pathOptimize; // boolean true

    public PrintSetting() {
    }

    public PrintSetting(final String newName, final PrintSetting other) {
        if (newName == null) {
            throw new IllegalArgumentException("newName must not be null");
        }
        name = newName;
        layerHeight = other.layerHeight;
        verticalShells = other.verticalShells;
        horizontalShells = other.horizontalShells;
        insideOut = other.insideOut;
        middleStart = other.middleStart;
        arcCompensation = other.arcCompensation;
        arcShortSides = other.arcShortSides;
        fillDensity = other.fillDensity;
        pattern = other.pattern;
        infillOverlap = other.infillOverlap;
        perimeterSpeed = other.perimeterSpeed;
        infillSpeed = other.infillSpeed;
        skirt = other.skirt;
        brimLines = other.brimLines;
        shield = other.shield;
        shieldStlFile = other.shieldStlFile;
        dumpX = other.dumpX;
        dumpY = other.dumpY;
        support = other.support;
        supportExtruder = other.supportExtruder;
        supportPattern = other.supportPattern;
        supportSpacing = other.supportSpacing;
        raftLayers = other.raftLayers;
        verboseGCode = other.verboseGCode;
        pathOptimize = other.pathOptimize;
    }

    @XmlTransient
    public double getLayerHeight() {
        return layerHeight;
    }

    public void setLayerHeight(final double layerHeight) {
        this.layerHeight = layerHeight;
    }

    @XmlTransient
    public int getVerticalShells() {
        return verticalShells;
    }

    public void setVerticalShells(final int verticalShells) {
        this.verticalShells = verticalShells;
    }

    @XmlTransient
    public int getHorizontalShells() {
        return horizontalShells;
    }

    public void setHorizontalShells(final int horizontalShells) {
        this.horizontalShells = horizontalShells;
    }

    @XmlTransient
    public double getFillDensity() {
        return fillDensity;
    }

    public void setFillDensity(final double fillDensity) {
        this.fillDensity = fillDensity;
    }

    @XmlTransient
    public FillPattern getFillPattern() {
        return pattern;
    }

    public void setFillPattern(final FillPattern pattern) {
        this.pattern = pattern;
    }

    @XmlTransient
    public double getPerimeterSpeed() {
        return perimeterSpeed;
    }

    public void setPerimeterSpeed(final double perimeterSpeed) {
        this.perimeterSpeed = perimeterSpeed;
    }

    @XmlTransient
    public double getInfillSpeed() {
        return infillSpeed;
    }

    public void setInfillSpeed(final double infillSpeed) {
        this.infillSpeed = infillSpeed;
    }

    public boolean printSkirt() {
        return skirt;
    }

    public void setSkirt(final boolean skirt) {
        this.skirt = skirt;
    }

    @XmlTransient
    public int getBrimLines() {
        return brimLines;
    }

    public void setBrimLines(final int brimLines) {
        this.brimLines = brimLines;
    }

    public boolean printShield() {
        return shield;
    }

    public void setShield(final boolean shield) {
        this.shield = shield;
    }

    @XmlTransient
    public int getDumpX() {
        return dumpX;
    }

    public void setDumpX(final int dumpX) {
        this.dumpX = dumpX;
    }

    @XmlTransient
    public int getDumpY() {
        return dumpY;
    }

    public void setDumpY(final int dumpY) {
        this.dumpY = dumpY;
    }

    public boolean printSupport() {
        return support;
    }

    public void setSupport(final boolean support) {
        this.support = support;
    }

    @XmlTransient
    public FillPattern getSupportPattern() {
        return supportPattern;
    }

    public void setSupportPattern(final FillPattern supportPattern) {
        this.supportPattern = supportPattern;
    }

    @XmlTransient
    public double getSupportSpacing() {
        return supportSpacing;
    }

    public void setSupportSpacing(final double supportSpacing) {
        this.supportSpacing = supportSpacing;
    }

    @XmlTransient
    public int getRaftLayers() {
        return raftLayers;
    }

    public void setRaftLayers(final int raftLayers) {
        this.raftLayers = raftLayers;
    }

    @XmlTransient
    public boolean isVerboseGCode() {
        return verboseGCode;
    }

    public void setVerboseGCode(final boolean verboseGCode) {
        this.verboseGCode = verboseGCode;
    }

    @XmlTransient
    public int getSupportExtruder() {
        return supportExtruder;
    }

    public void setSupportExtruder(final int supportExtruder) {
        this.supportExtruder = supportExtruder;
    }

    @XmlTransient
    public boolean isPathOptimize() {
        return pathOptimize;
    }

    public void setPathOptimize(final boolean pathOptimize) {
        this.pathOptimize = pathOptimize;
    }

    @XmlTransient
    public boolean isInsideOut() {
        return insideOut;
    }

    public void setInsideOut(final boolean insideOut) {
        this.insideOut = insideOut;
    }

    @XmlTransient
    public double getArcCompensation() {
        return arcCompensation;
    }

    public void setArcCompensation(final double arcCompensation) {
        this.arcCompensation = arcCompensation;
    }

    @XmlTransient
    public double getArcShortSides() {
        return arcShortSides;
    }

    public void setArcShortSides(final double arcShortSides) {
        this.arcShortSides = arcShortSides;
    }

    @XmlTransient
    public double getInfillOverlap() {
        return infillOverlap;
    }

    public void setInfillOverlap(final double infillOverlap) {
        this.infillOverlap = infillOverlap;
    }

    public File getShieldStlFile() {
        return new File(Configuration.REPRAP_DIRECTORY, shieldStlFile);
    }

    public void setShieldStlFile(final String shieldStlFile) {
        this.shieldStlFile = shieldStlFile;
    }

    @Override
    @XmlTransient
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
