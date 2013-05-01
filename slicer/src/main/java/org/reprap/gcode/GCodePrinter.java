package org.reprap.gcode;

/*
 * TODO: fixup warmup segments GCode (forgets to turn on extruder) 
 * TODO: fixup all the RR: println commands 
 * TODO: find a better place for the code. You cannot even detect a layer change without hacking now. 
 * TODO: read Zach's GCode examples to check if I messed up. 
 * TODO: make GCodeWriter a subclass of NullCartesian, so I don't have to fix code all over the place.
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JOptionPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Constants;
import org.reprap.configuration.PreferenceChangeListener;
import org.reprap.configuration.Preferences;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.Attributes;

public class GCodePrinter implements PreferenceChangeListener {
    private static final Logger LOGGER = LogManager.getLogger(GCodePrinter.class);
    private final GCodeWriter gcode = new GCodeWriter();
    /**
     * Force an extruder to be selected on startup
     */
    private boolean forceSelection = true;
    /**
     * Have we actually used this extruder?
     */
    private boolean physicalExtruderUsed[];
    private JCheckBoxMenuItem layerPauseCheckbox = null;
    /**
     * Current X position of the extruder
     */
    private double currentX = 0;
    /**
     * Current Y position of the extruder
     */
    private double currentY = 0;
    /**
     * Current Z position of the extruder
     */
    private double currentZ = 0;
    /**
     * Maximum feedrate for Z axis
     */
    private double maxFeedrateZ;
    /**
     * Current feedrate for the machine.
     */
    private double currentFeedrate = 0;
    /**
     * Feedrate for fast XY moves on the machine.
     */
    private double fastXYFeedrate;
    /**
     * The fastest the machine can accelerate in X and Y
     */
    private double maxXYAcceleration;
    /**
     * The fastest the machine can accelerate in Z
     */
    private double maxZAcceleration;
    /**
     * Feedrate for fast Z moves on the machine.
     */
    private double fastFeedrateZ;
    /**
     * Array containing the extruders on the 3D printer
     */
    private GCodeExtruder extruders[];
    private int currentExtruder;
    private int foundationLayers = 0;
    /**
     * The maximum X and Y point we can move to
     */
    private Point2D bedNorthEast;
    private double maximumXvalue;
    private double maximumYvalue;
    private double maximumZvalue;
    private boolean relativeExtrusion;
    private final Preferences preferences = Preferences.getInstance();
    private Purge purge;

    public GCodePrinter() throws IOException {
        refreshPreferences(preferences);
        gcode.writeCommand("M110", "reset the line numbers");
    }

    private void loadExtruders() {
        final int extruderCount = preferences.loadInt("NumberOfExtruders");
        if (extruderCount < 1) {
            throw new IllegalStateException("A Reprap printer must contain at least one extruder.");
        }
        extruders = new GCodeExtruder[extruderCount];

        // TODO: construct first all logical extruders (without extrusion state), then construct extrusion state for each physical extruder and
        // then make the assignment to logical extruders.
        int physExCount = -1;
        for (int i = 0; i < extruders.length; i++) {
            extruders[i] = new GCodeExtruder(gcode, i, this);

            // Make sure all instances of each physical extruder share the same
            // ExtrudedLength instance
            final int pe = extruders[i].getPhysicalExtruderNumber();
            if (pe > physExCount) {
                physExCount = pe;
            }
            for (int j = 0; j < i; j++) {
                if (extruders[j].getPhysicalExtruderNumber() == pe) {
                    extruders[i].setExtrudeState(extruders[j].getExtruderState());
                    break;
                }
            }
        }
        physicalExtruderUsed = new boolean[physExCount + 1];
        for (int i = 0; i <= physExCount; i++) {
            physicalExtruderUsed[i] = false;
        }
        currentExtruder = 0;
    }

    private void qFeedrate(final double feedrate) {
        if (currentFeedrate == feedrate) {
            return;
        }
        gcode.writeCommand("G1 F" + feedrate, "feed for start of next move");
        currentFeedrate = feedrate;
    }

    private void qXYMove(final double x, final double y, double feedrate) {
        final double dx = x - currentX;
        final double dy = y - currentY;

        double extrudeLength = extruders[currentExtruder].getDistance(Math.sqrt(dx * dx + dy * dy));
        String se = "";

        if (extrudeLength > 0) {
            if (extruders[currentExtruder].getReversing()) {
                extrudeLength = -extrudeLength;
            }
            extruders[currentExtruder].getExtruderState().add(extrudeLength);
            if (relativeExtrusion) {
                se = " E" + round(extrudeLength, 3);
            } else {
                se = " E" + round(extruders[currentExtruder].getExtruderState().length(), 3);
            }
        }

        final double xyFeedrate = round(extruders[currentExtruder].getFastXYFeedrate(), 1);
        if (xyFeedrate < feedrate && Math.abs(extrudeLength) > Constants.TINY_VALUE) {
            LOGGER.debug("GCodeRepRap().qXYMove: extruding feedrate (" + feedrate + ") exceeds maximum (" + xyFeedrate + ").");
            feedrate = xyFeedrate;
        }

        if (getExtruder().getMaxAcceleration() <= 0) {
            qFeedrate(feedrate);
        }

        if (dx == 0.0 && dy == 0.0) {
            if (currentFeedrate != feedrate) {
                qFeedrate(feedrate);
            }
            return;
        }

        String s = "G1 ";
        if (dx != 0) {
            s += "X" + x;
        }
        if (dy != 0) {
            s += " Y" + y;
        }

        s += se;
        if (currentFeedrate != feedrate) {
            s += " F" + feedrate;
            currentFeedrate = feedrate;
        }
        gcode.writeCommand(s, "horizontal move");
        currentX = x;
        currentY = y;
    }

    private void qZMove(final double z, double feedrate) {
        // note we set the feedrate whether we move or not

        final double zFeedrate = round(getMaxFeedrateZ(), 1);

        if (zFeedrate < feedrate) {
            LOGGER.debug("GCodeRepRap().qZMove: feedrate (" + feedrate + ") exceeds maximum (" + zFeedrate + ").");
            feedrate = zFeedrate;
        }

        if (getMaxZAcceleration() <= 0) {
            qFeedrate(feedrate);
        }

        final double dz = z - currentZ;

        if (dz == 0.0) {
            return;
        }

        String s = "G1 Z" + z;
        double extrudeLength = extruders[currentExtruder].getDistance(dz);
        if (extrudeLength > 0) {
            if (extruders[currentExtruder].getReversing()) {
                extrudeLength = -extrudeLength;
            }
            extruders[currentExtruder].getExtruderState().add(extrudeLength);
            if (relativeExtrusion) {
                s += " E" + round(extrudeLength, 3);
            } else {
                s += " E" + round(extruders[currentExtruder].getExtruderState().length(), 3);
            }
        }
        if (currentFeedrate != feedrate) {
            s += " F" + feedrate;
            currentFeedrate = feedrate;
        }
        gcode.writeCommand(s, "z move");
        currentZ = z;
    }

    public void moveTo(double x, double y, double z, double feedrate, final boolean lift) {
        x = checkCoordinate("x", x, 0, maximumXvalue);
        y = checkCoordinate("y", y, 0, maximumYvalue);
        z = checkCoordinate("z", z, 0, maximumZvalue);
        x = round(x, 2);
        y = round(y, 2);
        z = round(z, 4);
        feedrate = round(feedrate, 1);

        final double dx = x - currentX;
        final double dy = y - currentY;
        final double dz = z - currentZ;

        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            return;
        }

        // This should either be a Z move or an XY move, but not all three
        final boolean zMove = dz != 0;
        final boolean xyMove = dx != 0 || dy != 0;

        if (zMove && xyMove) {
            LOGGER.debug("GcodeRepRap.moveTo(): attempt to move in X|Y and Z simultaneously: (x, y, z) = (" + currentX + "->"
                    + x + ", " + currentY + "->" + y + ", " + currentZ + "->" + z + ", " + ")");
        }

        //go up first?
        if (lift) {
            final double liftIncrement = extruders[currentExtruder].getLift();
            final double liftedZ = round(currentZ + liftIncrement, 4);
            qZMove(liftedZ, round(getMaxFeedrateZ(), 1));
            qFeedrate(feedrate);
        }

        if (dz > 0) {
            if (zMove) {
                qZMove(z, feedrate);
            }
            if (xyMove) {
                qXYMove(x, y, feedrate);
            }
        } else {
            if (xyMove) {
                qXYMove(x, y, feedrate);
            }
            if (zMove) {
                qZMove(z, feedrate);
            }
        }

        checkCoordinates(x, y, z);

        currentX = x;
        currentY = y;
        currentZ = z;
    }

    private void checkCoordinates(final double x, final double y, final double z) {
        checkCoordinate("x", x, 0, maximumXvalue);
        checkCoordinate("y", y, 0, maximumYvalue);
        checkCoordinate("z", z, 0, maximumZvalue);
    }

    private double checkCoordinate(final String name, final double value, final double minimumValue, final double maximumValue) {
        if (value > maximumValue || value < minimumValue) {
            LOGGER.error("Attempt to move " + name + " to " + value + " which is outside [" + minimumValue + ", "
                    + maximumValue + "]");
        }
        return Math.max(0, Math.min(value, maximumValue));
    }

    /**
     * make a single, usually non-building move (between plots, or zeroing an
     * axis etc.)
     */
    public void singleMove(double x, double y, double z, final double feedrate, final boolean really) {
        final double x0 = getX();
        final double y0 = getY();
        final double z0 = getZ();
        x = round(x, 2);
        y = round(y, 2);
        z = round(z, 4);
        final double dx = x - x0;
        final double dy = y - y0;
        final double dz = z - z0;

        final boolean zMove = dz != 0;
        final boolean xyMove = dx != 0 || dy != 0;

        if (zMove && xyMove) {
            LOGGER.debug("GcodeRepRap.singleMove(): attempt to move in X|Y and Z simultaneously: (x, y, z) = (" + x + ", " + y
                    + ", " + z + ")");
        }

        if (!really) {
            currentX = x;
            currentY = y;
            currentZ = z;
            currentFeedrate = feedrate;
            return;
        }

        moveTo(x, y, z, feedrate, false);
    }

    public void printTo(final double x, final double y, final double z, final double feedrate, final boolean stopExtruder) {
        moveTo(x, y, z, feedrate, false);

        if (stopExtruder) {
            getExtruder().stopExtruding();
        }
    }

    public void startRun(final Rectangle rectangle, final double machineZ, final double machineMaxZ) throws IOException {
        gcode.writeComment(" GCode generated by RepRap Java Host Software");
        final Date myDate = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd:HH-mm-ss");
        final String myDateString = sdf.format(myDate);
        gcode.writeComment(" Created: " + myDateString);
        gcode.writeComment("#!RECTANGLE: " + rectangle + ", height: " + machineMaxZ);
        final boolean debugGcode = preferences.loadBool("Debug");
        if (debugGcode) {
            gcode.writeComment(" Prologue:");
        }
        gcode.copyFile(preferences.getPrologueFile());
        if (debugGcode) {
            gcode.writeComment(" ------");
        }
        currentX = 0;
        currentY = 0;
        currentZ = 0;
        currentFeedrate = -100; // Force it to set the feedrate at the start
        forceSelection = true; // Force it to set the extruder to use at the start

        if (preferences.loadBool("StartRectangle")) {
            // plot the outline
            plotOutlines(rectangle, machineZ);
        }
    }

    /**
     * Plot rectangles round the build on layer 0 or above
     */
    private void plotOutlines(Rectangle rectangle, final double machineZ) {
        boolean zRight = false;

        for (int e = extruders.length - 1; e >= 0; e--) { // Count down so we end with the one most likely to start the rest
            final int pe = extruders[e].getPhysicalExtruderNumber();
            if (physicalExtruderUsed[pe]) {
                if (!zRight) {
                    singleMove(currentX, currentY, preferences.getPrintSettings().getLayerHeight(), getFastFeedrateZ(), true);
                    currentZ = machineZ;
                }
                zRight = true;
                selectExtruder(e, true, false);
                final GCodeExtruder extruder = getExtruder();
                singleMove(rectangle.x().low(), rectangle.y().low(), currentZ, extruder.getFastXYFeedrate(), true);
                startExtruder(true);
                singleMove(rectangle.x().high(), rectangle.y().low(), currentZ, extruder.getFastXYFeedrate(), true);
                singleMove(rectangle.x().high(), rectangle.y().high(), currentZ, extruder.getFastXYFeedrate(), true);
                singleMove(rectangle.x().low(), rectangle.y().high(), currentZ, extruder.getFastXYFeedrate(), true);
                singleMove(rectangle.x().low(), rectangle.y().low(), currentZ, extruder.getFastXYFeedrate(), true);
                currentX = rectangle.x().low();
                currentY = rectangle.y().low();
                retract();
                rectangle = rectangle.offset(2 * extruder.getExtrusionSize());
                physicalExtruderUsed[pe] = false; // Stop us doing it again
            }
        }
    }

    public String startingLayer(final double zStep, final double machineZ, final int machineLayer, final int maxMachineLayer,
            final boolean reversing) {
        final String result;
        currentFeedrate = -1; // Force it to set the feedrate
        if (!reversing) {
            final File temporaryFile = gcode.openTemporaryOutFile(machineLayer);
            result = temporaryFile.getPath();
        } else {
            gcode.writeComment("#!LAYER: " + machineLayer + "/" + (maxMachineLayer - 1));
            result = null;
        }

        getExtruder().zeroExtrudedLength(reversing);

        if (layerPauseCheckbox != null && layerPauseCheckbox.isSelected()) {
            layerPause();
        }

        setZ(machineZ - zStep);
        singleMove(getX(), getY(), machineZ, getFastFeedrateZ(), reversing);

        return result;
    }

    public void finishedLayer(final boolean reversing) {
        if (!reversing) {
            gcode.closeOutFile();
        }
    }

    public void terminate(final Point2D lastPoint, final double lastZ) throws IOException {
        currentX = round(lastPoint.x(), 2);
        currentY = round(lastPoint.y(), 2);
        currentZ = round(lastZ, 1);

        final boolean debugGcode = preferences.loadBool("Debug");
        if (debugGcode) {
            gcode.writeComment(" Epilogue:");
        }
        gcode.copyFile(preferences.getEpilogueFile());
        if (debugGcode) {
            gcode.writeComment(" ------");
        }
    }

    private static double round(final double c, final double d) {
        final double power = Math.pow(10.0, d);

        return Math.round(c * power) / power;
    }

    private void extrude(final double distance, final double feedRate) {
        final GCodeExtruder extruder = getExtruder();
        double extrudeLength = extruder.getDistance(distance);

        final double scaledFeedRate;
        if (extruder.getFeedDiameter() > 0) {
            scaledFeedRate = feedRate * preferences.getPrintSettings().getLayerHeight() * extruder.getExtrusionSize()
                    / (extruder.getFeedDiameter() * extruder.getFeedDiameter() * Math.PI / 4);
        } else {
            scaledFeedRate = feedRate;
        }
        currentFeedrate = 0; // force it to output feedrate
        qFeedrate(round(scaledFeedRate, 1));
        if (extruder.getReversing()) {
            extrudeLength = -extrudeLength;
        }
        extruder.getExtruderState().add(extrudeLength);

        final String command;
        if (relativeExtrusion) {
            command = "G1 E" + round(extrudeLength, 3);
        } else {
            command = "G1 E" + round(extruder.getExtruderState().length(), 3);
        }
        final String comment;
        if (extruder.getReversing()) {
            comment = "extruder retraction";
        } else {
            comment = "extruder dwell";
        }
        gcode.writeCommand(command, comment);
        qFeedrate(extruder.getFastXYFeedrate());
    }

    public void setGCodeFileForOutput(final File gcodeFile) throws FileNotFoundException {
        gcode.setGCodeFileForOutput(gcodeFile);
    }

    /**
     * Tell the printer class it's Z position. Only to be used if you know what
     * you're doing...
     */
    public void setZ(final double z) {
        currentZ = round(z, 4);
    }

    private void selectExtruder(final int materialIndex, final boolean really, final boolean update) {
        final GCodeExtruder oldExtruder = getExtruder();
        final int newPhysicalExtruder = extruders[materialIndex].getPhysicalExtruderNumber();
        final boolean shield = preferences.loadBool("Shield");

        if (newPhysicalExtruder != oldExtruder.getPhysicalExtruderNumber() || forceSelection) {
            if (really) {
                oldExtruder.stopExtruding();

                if (materialIndex < 0 || materialIndex >= extruders.length) {
                    LOGGER.error("Selected material (" + materialIndex + ") is out of range.");
                    currentExtruder = 0;
                } else {
                    currentExtruder = materialIndex;
                }

                if (update) {
                    physicalExtruderUsed[newPhysicalExtruder] = true;
                }
                final GCodeExtruder extruder = getExtruder();
                extruder.stopExtruding(); // Make sure we are off

                if (shield) {
                    final Point2D purgePoint = purge.getPurgeEnd(true, 0);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();
                }

                // Now tell the GCodes to select the new extruder and stabilise all temperatures
                gcode.writeCommand("T" + newPhysicalExtruder, "select new extruder");

                if (shield) {
                    // Plot the purge pattern with the new extruder
                    startExtruder(true);

                    Point2D purgePoint = purge.getPurgeEnd(false, 0);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(false, 1);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(true, 1);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(true, 2);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(false, 2);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    retract();
                    extruder.stopExtruding();
                }
            }
            forceSelection = false;
        }
    }

    /**
     * Force the output stream to be some value - use with caution
     */
    public void forceOutputFile(final PrintStream fos) {
        gcode.forceOutputFile(fos);
    }

    /**
     * Return the name if the gcode file
     */
    public String getOutputFilename() {
        return gcode.getOutputFilename();
    }

    public void selectExtruder(final Attributes att) {
        for (int i = 0; i < extruders.length; i++) {
            if (att.getMaterial().equals(extruders[i].getMaterial())) {
                selectExtruder(i, true, true);
                return;
            }
        }
        LOGGER.error("selectExtruder() - extruder not found for: " + att.getMaterial());
    }

    public double getX() {
        return currentX;
    }

    public double getY() {
        return currentY;
    }

    public double getZ() {
        return currentZ;
    }

    public GCodeExtruder getExtruder(final String name) {
        for (final GCodeExtruder extruder2 : extruders) {
            if (name.equals(extruder2.toString())) {
                return extruder2;
            }
        }
        return null;
    }

    public GCodeExtruder getExtruder() {
        return extruders[currentExtruder];
    }

    public GCodeExtruder[] getExtruders() {
        return extruders;
    }

    public void startExtruder(final boolean firstOneInLayer) {
        final GCodeExtruder extruder = getExtruder();
        final double retraction = extruder.getExtruderState().retraction();

        if (retraction > 0) {
            extruder.startExtrusion(false);
            extrude(retraction, extruder.getFastEFeedrate());
            extruder.getExtruderState().setRetraction(0);
        }

        // Extrude motor delays (ms)
        double eDelay;
        if (firstOneInLayer) {
            eDelay = extruder.getExtraExtrusionForLayer();
        } else {
            eDelay = extruder.getExtraExtrusionForPolygon();
        }

        extruder.startExtrusion(false);
        if (eDelay > 0) {
            extrude(eDelay, extruder.getFastXYFeedrate());
        }
    }

    /**
     * Extrude backwards for the configured time in milliseconds, so that
     * polymer is stopped flowing at the end of a track.
     */
    public void retract() {
        final GCodeExtruder extruder = getExtruder();
        final double distance = extruder.getRetractionDistance();

        if (distance <= 0) {
            return;
        }

        extruder.startExtrusion(true);
        extrude(distance, extruder.getFastEFeedrate());
        extruder.stopExtruding();
        extruder.getExtruderState().setRetraction(extruder.getExtruderState().retraction() + distance);
    }

    private void setFastFeedrateZ(final double feedrate) {
        fastFeedrateZ = feedrate;
    }

    public double getFastFeedrateZ() {
        return fastFeedrateZ;
    }

    private double getMaxFeedrateZ() {
        return maxFeedrateZ;
    }

    /**
     * Display a message indicating a layer is about to be printed and wait for
     * the user to acknowledge
     */
    private void layerPause() {
        JOptionPane.showMessageDialog(null, "A new layer is about to be produced");
    }

    /**
     * Set the source checkbox used to determine if there should be a pause
     * between layers.
     * 
     * @param layerPause
     *            The source checkbox used to determine if there should be a
     *            pause. This is a checkbox rather than a boolean so it can be
     *            changed on the fly.
     */
    public void setLayerPause(final JCheckBoxMenuItem layerPause) {
        layerPauseCheckbox = layerPause;
    }

    public int getFoundationLayers() {
        return foundationLayers;
    }

    /**
     * Get the feedrate currently being used
     */
    public double getCurrentFeedrate() {
        return currentFeedrate;
    }

    /**
     * @return fast XY movement feedrate in mm/minute
     */
    public double getFastXYFeedrate() {
        return fastXYFeedrate;
    }

    /**
     * @return the fastest the machine can accelerate
     */
    public double getMaxXYAcceleration() {
        return maxXYAcceleration;
    }

    private double getMaxZAcceleration() {
        return maxZAcceleration;
    }

    public void forceNextExtruder() {
        forceSelection = true;
    }

    /**
     * The XY point furthest from the origin
     */
    public Point2D getBedNorthEast() {
        return bedNorthEast;
    }

    @Override
    public void refreshPreferences(final Preferences prefs) {
        maximumXvalue = prefs.loadDouble("WorkingX(mm)");
        maximumYvalue = prefs.loadDouble("WorkingY(mm)");
        maximumZvalue = prefs.loadDouble("WorkingZ(mm)");
        bedNorthEast = new Point2D(maximumXvalue, maximumYvalue);
        relativeExtrusion = prefs.loadBool("ExtrusionRelative");

        // Load our maximum feedrate variables
        final double maxFeedrateX = prefs.loadDouble("MaximumFeedrateX(mm/minute)");
        final double maxFeedrateY = prefs.loadDouble("MaximumFeedrateY(mm/minute)");
        maxFeedrateZ = prefs.loadDouble("MaximumFeedrateZ(mm/minute)");

        maxXYAcceleration = prefs.loadDouble("MaxXYAcceleration(mm/mininute/minute)");
        maxZAcceleration = prefs.loadDouble("MaxZAcceleration(mm/mininute/minute)");

        //set our standard feedrates.
        fastXYFeedrate = Math.min(maxFeedrateX, maxFeedrateY);
        setFastFeedrateZ(maxFeedrateZ);

        foundationLayers = prefs.loadInt("FoundationLayers");
        loadExtruders();
    }

    public void setPurge(final Purge purge) {
        this.purge = purge;
    }
}
