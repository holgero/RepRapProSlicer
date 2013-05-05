package org.reprap.gcode;

import static org.reprap.configuration.MathRoutines.circleAreaForDiameter;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.ExtruderSetting;
import org.reprap.configuration.MaterialSetting;

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
     * The diameter of the feedstock (if any)
     */
    private double feedDiameter;
    /**
     * Identifier of the extruder
     */
    private final int myExtruderID;
    /**
     * How high to move above the surface for non-extruding movements
     */
    private double lift;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    private double extrusionOverRun;
    private double extrudeRatio = 1;
    private final GCodePrinter printer;
    private double retractionDistance;
    private double extraExtrusionForLayer;
    private double extraExtrusionForPolygon;
    private MaterialSetting materialSettings;

    GCodeExtruder(final GCodeWriter writer, final int extruderId, final GCodePrinter p) {
        gcode = writer;
        myExtruderID = extruderId;
        printer = p;
        loadPreferences(Configuration.getInstance().getCurrentConfiguration().getPrinterSetting().getExtruderSettings()
                .get(extruderId));
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

    private void loadPreferences(final ExtruderSetting extruderSetting) {
        extrusionSize = extruderSetting.getNozzleDiameter();
        retractionDistance = extruderSetting.getRetraction();
        extraExtrusionForLayer = extruderSetting.getExtraLengthPerLayer();
        extraExtrusionForPolygon = extruderSetting.getExtraLengthPerPolygon();
        extrudeRatio = extruderSetting.getExtrudeRatio();
        extrusionOverRun = extruderSetting.getExtrusionOverrun();
        fastEFeedrate = extruderSetting.getAirExtrusionFeedRate();
        fastXYFeedrate = Math.min(printer.getFastXYFeedrate(), extruderSetting.getPrintExtrusionRate());
        lift = extruderSetting.getLift();
        materialSettings = extruderSetting.getMaterial();
        feedDiameter = materialSettings.getDiameter();
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

    @Override
    public String toString() {
        return "Extruder " + myExtruderID;
    }

    public double getExtrusionOverRun() {
        return extrusionOverRun;
    }

    /**
     * What stuff are we working with?
     */
    public MaterialSetting getMaterial() {
        return materialSettings;
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

        final double layerHeight = Configuration.getInstance().getCurrentConfiguration().getPrintSetting().getLayerHeight();
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

    public double getLift() {
        return lift;
    }

    /**
     * The diameter of the input filament
     */
    double getFeedDiameter() {
        return feedDiameter;
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