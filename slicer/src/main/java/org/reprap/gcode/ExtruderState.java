package org.reprap.gcode;

/**
 * Holds the length of filament that an extruder has extruded so far, and other
 * aspects of the state of the extruder. This is used in the extruder class. All
 * logical extruders that correspond to one physical extruder share a single
 * instance of this class. This means that (for example) setting the temperature
 * of one will be reflected automatically in all the others.
 * 
 * @author Adrian
 */
public class ExtruderState {
    private double extrudedLength;
    private double retractionAmount;
    private boolean isReversed;
    private boolean isExtruding;

    ExtruderState() {
        extrudedLength = 1;
        retractionAmount = 0;
        isReversed = false;
        isExtruding = false;
    }

    public double length() {
        return extrudedLength;
    }

    boolean reverse() {
        return isReversed;
    }

    boolean isExtruding() {
        return isExtruding;
    }

    public void add(final double e) {
        extrudedLength += e;
    }

    void zero() {
        extrudedLength = 0;
    }

    void setReverse(final boolean rev) {
        isReversed = rev;
    }

    void setExtruding(final boolean ex) {
        isExtruding = ex;
    }

    public void setRetraction(final double r) {
        retractionAmount = r;
    }

    public double retraction() {
        return retractionAmount;
    }
}
