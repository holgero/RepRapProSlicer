package org.reprap.gcode;

import static org.reprap.configuration.store.MathRoutines.circleAreaForDiameter;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.ExtruderSetting;
import org.reprap.configuration.MaterialSetting;
import org.reprap.configuration.PrinterSetting;

public class GCodeExtruder {
    private final GCodeWriter gcode;
    /**
     * How far we have extruded plus other things like temperature
     */
    private final ExtruderState extruderState = new ExtruderState();
    /**
     * The extrusion width in XY
     */
    private final double extrusionSize;
    /**
     * The fastest speed of movement in XY when depositing
     */
    private final double fastXYFeedrate;
    /**
     * The fastest the extruder can move the filament (without actually
     * extruding)
     */
    private final double fastEFeedrate;
    /**
     * The diameter of the feedstock (if any)
     */
    private final double feedDiameter;
    /**
     * Identifier of the extruder
     */
    private final int myExtruderID;
    /**
     * How high to move above the surface for non-extruding movements
     */
    private final double lift;
    /**
     * The number of mm to stop extruding before the end of a track
     */
    private final double extrusionOverRun;
    private final double extrudeRatio;
    private final double retractionDistance;
    private final double extraExtrusionForLayer;
    private final double extraExtrusionForPolygon;
    private final double layerHeight;
    private final MaterialSetting materialSettings;

    GCodeExtruder(final GCodeWriter writer, final int extruderId, final CurrentConfiguration currentConfiguration) {
        gcode = writer;
        myExtruderID = extruderId;
        layerHeight = currentConfiguration.getPrintSetting().getLayerHeight();
        final PrinterSetting printerSetting = currentConfiguration.getPrinterSetting();
        final ExtruderSetting extruderSetting = printerSetting.getExtruderSettings().get(extruderId);
        extrusionSize = extruderSetting.getNozzleDiameter();
        retractionDistance = extruderSetting.getRetraction();
        extraExtrusionForLayer = extruderSetting.getExtraLengthPerLayer();
        extraExtrusionForPolygon = extruderSetting.getExtraLengthPerPolygon();
        extrudeRatio = extruderSetting.getExtrudeRatio();
        extrusionOverRun = extruderSetting.getExtrusionOverrun();
        fastEFeedrate = extruderSetting.getAirExtrusionFeedRate();
        final double maxXYFeedrate = Math.min(printerSetting.getMaximumFeedrateX(), printerSetting.getMaximumFeedrateY());
        fastXYFeedrate = Math.min(maxXYFeedrate, extruderSetting.getPrintExtrusionRate());
        lift = extruderSetting.getLift();
        materialSettings = currentConfiguration.getMaterials().get(extruderId);
        feedDiameter = materialSettings.getDiameter();

        // when we are first called (top down calculation means at our top
        // layer) the layer below will have reversed us at its end on the way up
        // in the actual build.
        extruderState.setRetraction(retractionDistance);
    }

    void zeroExtrudedLength() {
        extruderState.zero();
        gcode.writeCommand("G92 E0", "zero the extruded length");
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