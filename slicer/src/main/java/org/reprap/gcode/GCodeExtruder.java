package org.reprap.gcode;

import static org.reprap.configuration.MathRoutines.circleAreaForDiameter;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.reprap.configuration.Constants;
import org.reprap.configuration.ExtruderSettings;
import org.reprap.configuration.Preferences;

public class GCodeExtruder {
    private final GCodeWriter gcode;
    /**
     * How far we have extruded plus other things like temperature
     */
    private final ExtruderState extruderState;
    /**
     * The extrusion width in XY
     */
    private double extrusionSize;
    /**
     * The fastest speed of movement in XY when depositing
     */
    private double fastXYFeedrate;
    /**
     * The fastest the extruder can extrude
     */
    private double fastEFeedrate;
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
    private double arcCompensationFactor;
    private double arcShortSides;
    private double extrudeRatio = 1;
    private boolean middleStart;
    private boolean insideOut = false;
    private final GCodePrinter printer;
    private double retractionDistance;
    private double extraExtrusionForLayer;
    private double extraExtrusionForPolygon;

    GCodeExtruder(final GCodeWriter writer, final int extruderId, final GCodePrinter p) {
        gcode = writer;
        myExtruderID = extruderId;
        printer = p;
        final Preferences preferences = Preferences.getInstance();
        loadPreferences(preferences, preferences.getPrinterSettings().getExtruderSettings()[extruderId]);
        extruderState = new ExtruderState();
        // when we are first called (top down calculation means at our top
        // layer) the layer below will have reversed us at its end on the way up
        // in the actual build.
        extruderState.setRetraction(getRetractionDistance() + extruderState.retraction());
    }

    /**
     * Zero the extruded length
     */
    void zeroExtrudedLength(final boolean really) {
        extruderState.zero();
        if (really) {
            gcode.writeCommand("G92 E0", "zero the extruded length");
        }
    }

    private void loadPreferences(final Preferences preferences, final ExtruderSettings extruderSettings) {
        extrusionSize = extruderSettings.getNozzleDiameter();
        final String prefName = "Extruder" + myExtruderID + "_";
        fastXYFeedrate = preferences.loadDouble(prefName + "FastXYFeedrate(mm/minute)");
        fastEFeedrate = preferences.loadDouble(prefName + "FastEFeedrate(mm/minute)");
        middleStart = preferences.loadBool(prefName + "MiddleStart");
        material = preferences.loadString(prefName + "MaterialType(name)");
        infillOverlap = preferences.loadDouble(prefName + "InfillOverlap(mm)");
        extraExtrusionForLayer = preferences.loadDouble(prefName + "ExtraExtrusionDistanceForLayer(mm)");
        extraExtrusionForPolygon = preferences.loadDouble(prefName + "ExtraExtrusionDistanceForPolygon(mm)");
        retractionDistance = preferences.loadDouble(prefName + "RetractionDistance(mm)");
        extrusionOverRun = preferences.loadDouble(prefName + "ExtrusionOverRun(mm)");
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

    void startExtrusion(final boolean reverse) {
        extruderState.setExtruding(true);
        extruderState.setReverse(reverse);
    }

    public void stopExtruding() {
        if (extruderState.isExtruding()) {
            extruderState.setExtruding(false);
            extruderState.setReverse(false);
        }
    }

    public double getFastXYFeedrate() {
        return fastXYFeedrate;
    }

    double getFastEFeedrate() {
        return fastEFeedrate;
    }

    public double getExtrusionSize() {
        return extrusionSize;
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
    boolean getReversing() {
        return extruderState.reverse();
    }

    /**
     * Get how much filament must be extruded for a given xy movement.
     */
    double getDistance(final double distance) {
        if (!extruderState.isExtruding()) {
            return 0;
        }
        if (feedDiameter < 0) {
            return extrudeRatio * distance;
        }

        final double layerHeight = Preferences.getInstance().getPrintSettings().getLayerHeight();
        return extrudeRatio * distance * layerHeight * extrusionSize / circleAreaForDiameter(feedDiameter);
    }

    /**
     * Find out how far we have extruded so far
     */
    ExtruderState getExtruderState() {
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
    double getFeedDiameter() {
        return feedDiameter;
    }

    /**
     * Plot perimiters inside out or outside in?
     */
    public boolean getInsideOut() {
        return insideOut;
    }

    double getRetractionDistance() {
        return retractionDistance;
    }

    double getExtraExtrusionForLayer() {
        return extraExtrusionForLayer;
    }

    double getExtraExtrusionForPolygon() {
        return extraExtrusionForPolygon;
    }

}