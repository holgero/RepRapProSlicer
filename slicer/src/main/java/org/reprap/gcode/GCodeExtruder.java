package org.reprap.gcode;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.reprap.configuration.Constants;
import org.reprap.configuration.Preferences;

public class GCodeExtruder {
    private final GCodeWriter gcode;
    /**
     * How far we have extruded plus other things like temperature
     */
    private ExtruderState extruderState;
    /**
     * The extrusion width in XY
     */
    private double extrusionSize;
    /**
     * below this infill finely
     */
    private int lowerFineLayers;
    /**
     * The fastest speed of movement in XY when depositing
     */
    private double fastXYFeedrate;
    /**
     * The fastest the extruder can extrude
     */
    private double fastEFeedrate;
    /**
     * Factor by which to speed up round corners
     */
    private double asFactor;
    /**
     * Factor for short line speeds
     */
    private double shortSpeed;
    /**
     * The name of this extruder's material
     */
    private String material;
    /**
     * Number of mm to overlap the hatching infill with the outline. 0 gives
     * none; -ve will leave a gap between the two
     */
    private double infillOverlap;
    /**
     * The diameter of the feedstock (if any)
     */
    private double feedDiameter;
    /**
     * Identifier of the extruder
     */
    private final int myExtruderID;
    /**
     * The colour of the material to use in the simulation windows
     */
    private Appearance materialColour;
    /**
     * How high to move above the surface for non-extruding movements
     */
    private double lift;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    private double extrusionOverRun;
    private double extrusionFoundationWidth;
    private double arcCompensationFactor;
    private double arcShortSides;
    private double extrudeRatio = 1;
    private boolean middleStart;
    private boolean insideOut = false;
    private final GCodePrinter printer;
    private double retractionDistance;
    private double extraExtrusionForLayer;
    private double extraExtrusionForPolygon;

    public GCodeExtruder(final GCodeWriter writer, final int extruderId, final GCodePrinter p) {
        gcode = writer;
        myExtruderID = extruderId;
        printer = p;
        loadPreferences(Preferences.getInstance());
        extruderState = new ExtruderState();
        // when we are first called (top down calculation means at our top
        // layer) the layer below will have reversed us at its end on the way up
        // in the actual build.
        extruderState.setRetraction(getRetractionDistance() + extruderState.retraction());
    }

    /**
     * Zero the extruded length
     */
    public void zeroExtrudedLength(final boolean really) {
        extruderState.zero();
        if (really) {
            gcode.writeCommand("G92 E0", "zero the extruded length");
        }
    }

    /**
     * Allow others to set our extrude length so that all logical extruders
     * talking to one physical extruder can use the same length instance.
     */
    public void setExtrudeState(final ExtruderState e) {
        extruderState = e;
    }

    private void loadPreferences(final Preferences preferences) {
        final String prefName = "Extruder" + myExtruderID + "_";
        extrusionSize = preferences.loadDouble(prefName + "ExtrusionSize(mm)");
        lowerFineLayers = 2;
        fastXYFeedrate = preferences.loadDouble(prefName + "FastXYFeedrate(mm/minute)");
        fastEFeedrate = preferences.loadDouble(prefName + "FastEFeedrate(mm/minute)");
        middleStart = preferences.loadBool(prefName + "MiddleStart");
        asFactor = 0.5;
        material = preferences.loadString(prefName + "MaterialType(name)");
        shortSpeed = 1;
        infillOverlap = preferences.loadDouble(prefName + "InfillOverlap(mm)");
        extraExtrusionForLayer = preferences.loadDouble(prefName + "ExtraExtrusionDistanceForLayer(mm)");
        extraExtrusionForPolygon = preferences.loadDouble(prefName + "ExtraExtrusionDistanceForPolygon(mm)");
        retractionDistance = preferences.loadDouble(prefName + "RetractionDistance(mm)");
        extrusionOverRun = preferences.loadDouble(prefName + "ExtrusionOverRun(mm)");
        extrusionFoundationWidth = preferences.loadDouble(prefName + "ExtrusionFoundationWidth(mm)");
        arcCompensationFactor = preferences.loadDouble(prefName + "ArcCompensationFactor(0..)");
        arcShortSides = preferences.loadDouble(prefName + "ArcShortSides(0..)");
        extrudeRatio = preferences.loadDouble(prefName + "ExtrudeRatio(0..)");
        lift = preferences.loadDouble(prefName + "Lift(mm)");
        final Color3f col = new Color3f((float) preferences.loadDouble(prefName + "ColourR(0..1)"),
                (float) preferences.loadDouble(prefName + "ColourG(0..1)"), (float) preferences.loadDouble(prefName
                        + "ColourB(0..1)"));
        materialColour = new Appearance();
        materialColour.setMaterial(new Material(col, Constants.BLACK, col, Constants.BLACK, 101f));
        feedDiameter = preferences.loadDouble(prefName + "FeedDiameter(mm)");
        insideOut = preferences.loadBool(prefName + "InsideOut");
        fastXYFeedrate = Math.min(printer.getFastXYFeedrate(), fastXYFeedrate);
    }

    public void startExtrusion(final boolean reverse) {
        extruderState.setExtruding(true);
        extruderState.setReverse(reverse);
    }

    public void stopExtruding() {
        if (extruderState.isExtruding()) {
            extruderState.setExtruding(false);
            extruderState.setReverse(false);
        }
    }

    public double getAngleFeedrate() {
        return asFactor * getFastXYFeedrate();
    }

    public double getFastXYFeedrate() {
        return fastXYFeedrate;
    }

    public double getFastEFeedrate() {
        return fastEFeedrate;
    }

    public double getExtrusionSize() {
        return extrusionSize;
    }

    public int getLowerFineLayers() {
        return lowerFineLayers;
    }

    public Appearance getAppearance() {
        return materialColour;
    }

    @Override
    public String toString() {
        // TODO: should this give more information?
        // CAVE!!! used in GCodePrinter to search for a extruder by material name! CAVE!!!
        return material;
    }

    /**
     * Feedrate for short lines in mm/minute
     */
    public double getShortLineFeedrate() {
        return shortSpeed * getFastXYFeedrate();
    }

    /**
     * Number of mm to overlap the hatching infill with the outline. 0 gives
     * none; -ve will leave a gap between the two
     */
    public double getInfillOverlap() {
        return infillOverlap;
    }

    public double getExtrusionOverRun() {
        return extrusionOverRun;
    }

    /**
     * What stuff are we working with?
     */
    public String getMaterial() {
        return material;
    }

    public double getExtrusionFoundationWidth() {
        return extrusionFoundationWidth;
    }

    /**
     * The arc compensation factor.
     */
    public double getArcCompensationFactor() {
        return arcCompensationFactor;
    }

    /**
     * The arc short sides.
     */
    public double getArcShortSides() {
        return arcShortSides;
    }

    /**
     * Find out if we are currently in reverse
     */
    public boolean getReversing() {
        return extruderState.reverse();
    }

    /**
     * Get how much filament must be extruded for a given xy movement.
     */
    public double getDistance(final double distance) {
        if (!extruderState.isExtruding()) {
            return 0;
        }
        if (feedDiameter < 0) {
            return extrudeRatio * distance;
        }

        final double layerHeight = Preferences.getInstance().getPrintSettings().getLayerHeight();
        return extrudeRatio * distance * layerHeight * extrusionSize / (feedDiameter * feedDiameter * Math.PI / 4);
    }

    /**
     * Find out how far we have extruded so far
     */
    public ExtruderState getExtruderState() {
        return extruderState;
    }

    /**
     * Each logical extruder has a unique ID
     */
    public int getID() {
        return myExtruderID;
    }

    /**
     * If this is true, plot outlines from the middle of their infilling hatch
     * to reduce dribble at their starts and ends. If false, plot the outline as
     * the outline.
     */
    public boolean getMiddleStart() {
        return middleStart;
    }

    public double getLift() {
        return lift;
    }

    /**
     * The diameter of the input filament
     */
    public double getFeedDiameter() {
        return feedDiameter;
    }

    /**
     * Plot perimiters inside out or outside in?
     */
    public boolean getInsideOut() {
        return insideOut;
    }

    public double getRetractionDistance() {
        return retractionDistance;
    }

    public double getExtraExtrusionForLayer() {
        return extraExtrusionForLayer;
    }

    public double getExtraExtrusionForPolygon() {
        return extraExtrusionForPolygon;
    }

}