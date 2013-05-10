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

    public double getLayerHeight() {
        return layerHeight;
    }

    void setLayerHeight(final double layerHeight) {
        this.layerHeight = layerHeight;
    }

    public int getVerticalShells() {
        return verticalShells;
    }

    void setVerticalShells(final int verticalShells) {
        this.verticalShells = verticalShells;
    }

    public int getHorizontalShells() {
        return horizontalShells;
    }

    void setHorizontalShells(final int horizontalShells) {
        this.horizontalShells = horizontalShells;
    }

    public double getFillDensity() {
        return fillDensity;
    }

    void setFillDensity(final double fillDensity) {
        this.fillDensity = fillDensity;
    }

    public FillPattern getFillPattern() {
        return pattern;
    }

    void setFillPattern(final FillPattern pattern) {
        this.pattern = pattern;
    }

    public double getPerimeterSpeed() {
        return perimeterSpeed;
    }

    void setPerimeterSpeed(final double perimeterSpeed) {
        this.perimeterSpeed = perimeterSpeed;
    }

    public double getInfillSpeed() {
        return infillSpeed;
    }

    void setInfillSpeed(final double infillSpeed) {
        this.infillSpeed = infillSpeed;
    }

    public boolean printSkirt() {
        return skirt;
    }

    void setSkirt(final boolean skirt) {
        this.skirt = skirt;
    }

    public int getBrimLines() {
        return brimLines;
    }

    void setBrimLines(final int brimLines) {
        this.brimLines = brimLines;
    }

    public boolean printShield() {
        return shield;
    }

    void setShield(final boolean shield) {
        this.shield = shield;
    }

    public int getDumpX() {
        return dumpX;
    }

    void setDumpX(final int dumpX) {
        this.dumpX = dumpX;
    }

    public int getDumpY() {
        return dumpY;
    }

    void setDumpY(final int dumpY) {
        this.dumpY = dumpY;
    }

    public boolean printSupport() {
        return support;
    }

    void setSupport(final boolean support) {
        this.support = support;
    }

    public FillPattern getSupportPattern() {
        return supportPattern;
    }

    void setSupportPattern(final FillPattern supportPattern) {
        this.supportPattern = supportPattern;
    }

    public double getSupportSpacing() {
        return supportSpacing;
    }

    void setSupportSpacing(final double supportSpacing) {
        this.supportSpacing = supportSpacing;
    }

    public int getRaftLayers() {
        return raftLayers;
    }

    void setRaftLayers(final int raftLayers) {
        this.raftLayers = raftLayers;
    }

    public boolean isVerboseGCode() {
        return verboseGCode;
    }

    void setVerboseGCode(final boolean verboseGCode) {
        this.verboseGCode = verboseGCode;
    }

    public int getSupportExtruder() {
        return supportExtruder;
    }

    void setSupportExtruder(final int supportExtruder) {
        this.supportExtruder = supportExtruder;
    }

    public boolean isPathOptimize() {
        return pathOptimize;
    }

    void setPathOptimize(final boolean pathOptimize) {
        this.pathOptimize = pathOptimize;
    }

    public boolean isInsideOut() {
        return insideOut;
    }

    void setInsideOut(final boolean insideOut) {
        this.insideOut = insideOut;
    }

    public boolean isMiddleStart() {
        return middleStart;
    }

    void setMiddleStart(final boolean middleStart) {
        this.middleStart = middleStart;
    }

    public double getArcCompensation() {
        return arcCompensation;
    }

    void setArcCompensation(final double arcCompensation) {
        this.arcCompensation = arcCompensation;
    }

    public double getArcShortSides() {
        return arcShortSides;
    }

    void setArcShortSides(final double arcShortSides) {
        this.arcShortSides = arcShortSides;
    }

    public double getInfillOverlap() {
        return infillOverlap;
    }

    void setInfillOverlap(final double infillOverlap) {
        this.infillOverlap = infillOverlap;
    }

    public File getShieldStlFile() {
        return new File(Configuration.getReprapDirectory(), shieldStlFile);
    }

    void setShieldStlFile(final String shieldStlFile) {
        this.shieldStlFile = shieldStlFile;
    }

    @Override
    public String getName() {
        return name;
    }

    void setName(final String name) {
        this.name = name;
    }
}
