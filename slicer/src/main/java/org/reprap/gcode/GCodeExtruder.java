package org.reprap.gcode;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.reprap.configuration.Constants;
import org.reprap.configuration.Preferences;

public class GCodeExtruder {
    private final GCodeWriter gcode;

    /**
     * Flag to decide extrude speed
     */
    private boolean separating = false;
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
     * The speed from which that machine can do a standing start with this
     * extruder
     */
    private double slowXYFeedrate;
    /**
     * The fastest the machine can accelerate with this extruder
     */
    private double maxAcceleration;
    /**
     * Length (mm) to speed up round corners
     */
    private double asLength;
    /**
     * Factor by which to speed up round corners
     */
    private double asFactor;
    /**
     * Line length below which to plot faster
     */
    private double shortLength;
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
    private double infillOverlap = 0;
    /**
     * The diameter of the feedstock (if any)
     */
    private double feedDiameter = -1;
    /**
     * Identifier of the extruder
     */
    private final int myExtruderID;
    /**
     * The colour of the material to use in the simulation windows
     */
    private Appearance materialColour;
    /**
     * The number of milliseconds to wait before starting a border track
     */
    private double extrusionDelayForLayer = 0;
    /**
     * How high to move above the surface for non-extruding movements
     */
    private double lift = 0;
    /**
     * The number of milliseconds to wait before starting a hatch track
     */
    private double extrusionDelayForPolygon = 0;
    /**
     * The number of milliseconds to reverse at the end of a track
     */
    private double extrusionReverseDelay = -1;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    private double extrusionOverRun;
    /**
     * The smallest allowable free-movement height above the base
     */
    private double minLiftedZ = 1;
    private double extrusionFoundationWidth;
    private double arcCompensationFactor;
    private double arcShortSides;
    private String supportMaterial;
    private double extrudeRatio = 1;
    private boolean middleStart;
    private boolean insideOut = false;
    private final GCodePrinter printer;
    private int physicalExtruderId;
    private double extrusionSpeed;
    private int supportExtruderNumber;

    public GCodeExtruder(final GCodeWriter writer, final int extruderId, final GCodePrinter p) {
        gcode = writer;
        myExtruderID = extruderId;
        printer = p;
        loadPreferences(Preferences.getInstance());
        extruderState = new ExtruderState(physicalExtruderId);
        final double delay = getExtrusionReverseDelay(); // when we are first called (top down calculation means at our top layer) the
        extruderState.setRetraction(extruderState.retraction() + delay); // layer below will have reversed us at its end on the way up in the actual build.
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

    public void setExtrusion(final double speed, final boolean reverse) {
        if (getExtruderSpeed() < 0) {
            return;
        }
        if (speed > 0) {
            extruderState.setExtruding(true);
        } else {
            extruderState.setExtruding(false);
        }
        extruderState.setSpeed(speed);
        extruderState.setReverse(reverse);
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
        physicalExtruderId = preferences.loadInt(prefName + "Address");
        extrusionSize = preferences.loadDouble(prefName + "ExtrusionSize(mm)");
        lowerFineLayers = 2;
        fastXYFeedrate = preferences.loadDouble(prefName + "FastXYFeedrate(mm/minute)");
        fastEFeedrate = preferences.loadDouble(prefName + "FastEFeedrate(mm/minute)");
        slowXYFeedrate = preferences.loadDouble(prefName + "SlowXYFeedrate(mm/minute)");
        maxAcceleration = preferences.loadDouble(prefName + "MaxAcceleration(mm/minute/minute)");
        extrusionSpeed = preferences.loadDouble(prefName + "ExtrusionSpeed(mm/minute)");
        middleStart = preferences.loadBool(prefName + "MiddleStart");
        asLength = -1;
        asFactor = 0.5;
        material = preferences.loadString(prefName + "MaterialType(name)");
        supportMaterial = preferences.loadString(prefName + "SupportMaterialType(name)");
        supportExtruderNumber = preferences.getNumberFromMaterial(supportMaterial);
        shortLength = -1;
        shortSpeed = 1;
        infillOverlap = preferences.loadDouble(prefName + "InfillOverlap(mm)");
        extrusionDelayForLayer = preferences.loadDouble(prefName + "ExtrusionDelayForLayer(ms)");
        extrusionDelayForPolygon = preferences.loadDouble(prefName + "ExtrusionDelayForPolygon(ms)");
        extrusionOverRun = preferences.loadDouble(prefName + "ExtrusionOverRun(mm)");
        extrusionReverseDelay = preferences.loadDouble(prefName + "Reverse(ms)");
        minLiftedZ = -1;
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
        slowXYFeedrate = Math.min(printer.getSlowXYFeedrate(), slowXYFeedrate);
        maxAcceleration = Math.min(printer.getMaxXYAcceleration(), maxAcceleration);
    }

    public void stopExtruding() {
        if (extruderState.isExtruding()) {
            setExtrusion(0, false);
            extruderState.setExtruding(false);
        }
    }

    public void setMotor(final boolean motorOn) {
        if (getExtruderSpeed() < 0) {
            return;
        }

        if (motorOn) {
            setExtrusion(getExtruderSpeed(), false);
            extruderState.setSpeed(getExtruderSpeed());
        } else {
            setExtrusion(0, false);
            extruderState.setSpeed(0);
        }
        extruderState.setReverse(false);
    }

    /**
     * The length in mm to speed up when going round corners (non-Javadoc)
     */
    public double getAngleSpeedUpLength() {
        return asLength;
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

    /**
     * @return slow XY movement feedrate in mm/minute
     */
    public double getSlowXYFeedrate() {
        return slowXYFeedrate;
    }

    /**
     * @return the fastest the machine can accelerate
     */
    public double getMaxAcceleration() {
        return maxAcceleration;
    }

    public void setSeparating(final boolean s) {
        separating = s;
    }

    public double getExtruderSpeed() {
        if (separating) {
            return 3000;
        } else {
            return extrusionSpeed;
        }

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
     * get short lengths which need to be plotted faster set -ve to turn this
     * off.
     */
    public double getShortLength() {
        return shortLength;
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

    /**
     * Gets the number of milliseconds to wait before starting a border track
     */
    public double getExtrusionDelayForLayer() {
        return extrusionDelayForLayer;
    }

    /**
     * Gets the number of milliseconds to wait before starting a hatch track
     */
    public double getExtrusionDelayForPolygon() {
        return extrusionDelayForPolygon;
    }

    /**
     * Gets the number of milliseconds to reverse the extrude motor at the end
     * of a track
     */
    public double getExtrusionReverseDelay() {
        return extrusionReverseDelay;
    }

    public double getExtrusionOverRun() {
        return extrusionOverRun;
    }

    /**
     * The smallest allowable free-movement height above the base
     */
    public double getMinLiftedZ() {
        return minLiftedZ;
    }

    /**
     * What stuff are we working with?
     */
    public String getMaterial() {
        return material;
    }

    public int getSupportExtruderNumber() {
        return supportExtruderNumber;
    }

    public GCodeExtruder getSupportExtruder() {
        return printer.getExtruder(supportMaterial);
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
     * If we are working with feedstock lengths, compute that from the actual
     * length we want to extrude from the nozzle, otherwise just return the
     * extruded length.
     */
    private double filamentDistance(final double distance) {
        if (getFeedDiameter() < 0) {
            return distance;
        }

        return distance * Preferences.getInstance().getPrintSettings().getLayerHeight() * getExtrusionSize()
                / (getFeedDiameter() * getFeedDiameter() * Math.PI / 4);
    }

    /**
     * Get how much extrudate is deposited in a given time (in milliseconds)
     * currentSpeed is in mm per minute. Valve extruders cannot know, so return
     * 0.
     */
    public double getDistanceFromTime(final double time) {
        if (!extruderState.isExtruding()) {
            return 0;
        }

        return filamentDistance(extrudeRatio * extruderState.speed() * time / 60000.0);
    }

    /**
     * Get how much extrudate is deposited for a given xy movement currentSpeed
     * is in mm per minute. Valve extruders cannot know, so return 0.
     */
    public double getDistance(final double distance) {
        if (!extruderState.isExtruding()) {
            return 0;
        }
        return filamentDistance(extrudeRatio * distance);
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
     * Several logical extruders can share one physical extruder This number is
     * unique to each physical extruder
     */
    public int getPhysicalExtruderNumber() {
        return extruderState.physicalExtruder();
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
}