package org.reprap.gcode;

import static org.reprap.configuration.MathRoutines.circleAreaForDiameter;

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
import org.reprap.configuration.Preferences;
import org.reprap.configuration.PrintSettings;
import org.reprap.configuration.PrinterSettings;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.Attributes;

public class GCodePrinter {
    private static final Logger LOGGER = LogManager.getLogger(GCodePrinter.class);
    private final GCodeWriter gcode = new GCodeWriter();
    /**
     * Force an extruder to be selected on startup
     */
    private boolean forceSelection = true;
    /**
     * Have we actually used this extruder?
     */
    private boolean extruderUsed[];
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
     * Current feedrate for the machine.
     */
    private double currentFeedrate = 0;
    /**
     * Array containing the extruders on the 3D printer
     */
    private GCodeExtruder extruders[];
    private int currentExtruder;
    private Purge purge;

    public GCodePrinter() throws IOException {
        loadExtruders();
        gcode.writeCommand("M110", "reset the line numbers");
    }

    private void loadExtruders() {
        final int extruderCount = getPrinterSettings().getExtruderSettings().size();
        if (extruderCount < 1) {
            throw new IllegalStateException("A Reprap printer must contain at least one extruder.");
        }
        extruders = new GCodeExtruder[extruderCount];
        for (int i = 0; i < extruders.length; i++) {
            extruders[i] = new GCodeExtruder(gcode, i, this);
        }
        extruderUsed = new boolean[extruderCount];
        for (int i = 0; i < extruderUsed.length; i++) {
            extruderUsed[i] = false;
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
        final GCodeExtruder extruder = extruders[currentExtruder];
        double extrudeLength = extruder.getDistance(Math.sqrt(dx * dx + dy * dy));
        String se = "";
        if (extrudeLength > 0) {
            if (extruder.getReversing()) {
                extrudeLength = -extrudeLength;
            }
            extruder.getExtruderState().add(extrudeLength);
            se = " " + getECoordinate(extrudeLength);
        }

        final double xyFeedrate = round(extruder.getFastXYFeedrate(), 1);
        if (xyFeedrate < feedrate && Math.abs(extrudeLength) > Constants.TINY_VALUE) {
            LOGGER.debug("GCodeRepRap().qXYMove: extruding feedrate (" + feedrate + ") exceeds maximum (" + xyFeedrate + ").");
            feedrate = xyFeedrate;
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

    private String getECoordinate(final double extrudeLength) {
        if (useRelativeExtrusion()) {
            return "E" + round(extrudeLength, 3);
        } else {
            return "E" + round(extruders[currentExtruder].getExtruderState().length(), 3);
        }
    }

    private void qZMove(final double z, double feedrate) {
        if (getFastFeedrateZ() < feedrate) {
            LOGGER.debug("GCodeRepRap().qZMove: feedrate (" + feedrate + ") exceeds maximum (" + getFastFeedrateZ() + ").");
            feedrate = getFastFeedrateZ();
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
            s = s + " " + getECoordinate(extrudeLength);
        }
        if (currentFeedrate != feedrate) {
            s += " F" + feedrate;
            currentFeedrate = feedrate;
        }
        gcode.writeCommand(s, "z move");
        currentZ = z;
    }

    public void moveTo(double x, double y, double z, double feedrate, final boolean lift) {
        x = checkCoordinate("x", x, 0, getMaximumXvalue());
        y = checkCoordinate("y", y, 0, getMaximumYvalue());
        z = checkCoordinate("z", z, 0, getMaximumZvalue());
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
            qZMove(liftedZ, getFastFeedrateZ());
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
        checkCoordinate("x", x, 0, getMaximumXvalue());
        checkCoordinate("y", y, 0, getMaximumYvalue());
        checkCoordinate("z", z, 0, getMaximumZvalue());
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
        final boolean debugGcode = getPrintSettings().isVerboseGCode();
        if (debugGcode) {
            gcode.writeComment(" Prologue:");
        }
        gcode.copyFile(getPrinterSettings().getPrologueFile());
        if (debugGcode) {
            gcode.writeComment(" ------");
        }
        currentX = 0;
        currentY = 0;
        currentZ = 0;
        currentFeedrate = -100; // Force it to set the feedrate at the start
        forceSelection = true; // Force it to set the extruder to use at the start

        if (getPrintSettings().printSkirt()) {
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
            if (extruderUsed[e]) {
                if (!zRight) {
                    singleMove(currentX, currentY, getPrintSettings().getLayerHeight(), getFastFeedrateZ(), true);
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
                extruderUsed[e] = false; // Stop us doing it again
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

        final boolean debugGcode = getPrintSettings().isVerboseGCode();
        if (debugGcode) {
            gcode.writeComment(" Epilogue:");
        }
        gcode.copyFile(getPrinterSettings().getEpilogueFile());
        if (debugGcode) {
            gcode.writeComment(" ------");
        }
    }

    private static double round(final double c, final double d) {
        final double power = Math.pow(10.0, d);

        return Math.round(c * power) / power;
    }

    private void extrude(final double extrudeLength, final double feedRate) {
        final GCodeExtruder extruder = getExtruder();

        final double scaledFeedRate;
        if (extruder.getFeedDiameter() > 0) {
            scaledFeedRate = feedRate * getPrintSettings().getLayerHeight() * extruder.getExtrusionSize()
                    / circleAreaForDiameter(extruder.getFeedDiameter());
        } else {
            scaledFeedRate = feedRate;
        }
        currentFeedrate = 0; // force it to output feedrate
        qFeedrate(round(scaledFeedRate, 1));
        final int sign = extruder.getReversing() ? -1 : 1;
        extruder.getExtruderState().add(sign * extrudeLength);
        final String command = "G1 " + getECoordinate(sign * extrudeLength);
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
        final GCodeExtruder newExtruder = extruders[materialIndex];
        final boolean shield = getPrintSettings().printShield();

        if (newExtruder != oldExtruder || forceSelection) {
            if (really) {
                oldExtruder.stopExtruding();

                if (materialIndex < 0 || materialIndex >= extruders.length) {
                    LOGGER.error("Selected material (" + materialIndex + ") is out of range.");
                    currentExtruder = 0;
                } else {
                    currentExtruder = materialIndex;
                }

                if (update) {
                    extruderUsed[materialIndex] = true;
                }
                final GCodeExtruder extruder = getExtruder();
                extruder.stopExtruding(); // Make sure we are off

                if (shield) {
                    final Point2D purgePoint = purge.getPurgeEnd(currentExtruder, true, 0);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();
                }

                // Now tell the GCodes to select the new extruder and stabilise all temperatures
                gcode.writeCommand("T" + newExtruder.getID(), "select new extruder");

                if (shield) {
                    // Plot the purge pattern with the new extruder
                    startExtruder(true);

                    Point2D purgePoint = purge.getPurgeEnd(currentExtruder, false, 0);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(currentExtruder, false, 1);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(currentExtruder, true, 1);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(currentExtruder, true, 2);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(currentExtruder, false, 2);
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
            if (att.getMaterial().equals(extruders[i].getMaterial().getName())) {
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
        for (final GCodeExtruder extruder : extruders) {
            if (name.equals(extruder.getMaterial().getName())) {
                return extruder;
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

        final double extraExtrusion;
        if (firstOneInLayer) {
            extraExtrusion = extruder.getExtraExtrusionForLayer();
        } else {
            extraExtrusion = extruder.getExtraExtrusionForPolygon();
        }

        extruder.startExtrusion(false);
        if (extraExtrusion > 0) {
            extrude(extraExtrusion, extruder.getFastXYFeedrate());
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

    public double getFastFeedrateZ() {
        return round(getPrinterSettings().getMaximumFeedrateZ(), 1);
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
        return Math.min(getPrinterSettings().getMaximumFeedrateX(), getPrinterSettings().getMaximumFeedrateY());
    }

    public void forceNextExtruder() {
        forceSelection = true;
    }

    /**
     * The XY point furthest from the origin
     */
    public Point2D getBedNorthEast() {
        return new Point2D(getMaximumXvalue(), getMaximumYvalue());
    }

    public void setPurge(final Purge purge) {
        this.purge = purge;
    }

    private double getMaximumXvalue() {
        return getPrinterSettings().getBedSizeX();
    }

    private double getMaximumYvalue() {
        return getPrinterSettings().getBedSizeY();
    }

    private double getMaximumZvalue() {
        return getPrinterSettings().getMaximumZ();
    }

    private boolean useRelativeExtrusion() {
        return getPrinterSettings().useRelativeDistanceE();
    }

    private PrintSettings getPrintSettings() {
        return Preferences.getCurrentConfiguration().getPrintSettings();
    }

    private PrinterSettings getPrinterSettings() {
        return Preferences.getCurrentConfiguration().getPrinterSettings();
    }
}
