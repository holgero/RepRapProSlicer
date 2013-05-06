package org.reprap.gcode;

import static org.reprap.configuration.MathRoutines.circleAreaForDiameter;

import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Configuration;
import org.reprap.configuration.MathRoutines;
import org.reprap.configuration.PrintSetting;
import org.reprap.configuration.PrinterSetting;
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

    public GCodePrinter() {
        loadExtruders();
        gcode.writeCommand("M110", "reset the line numbers");
    }

    private void loadExtruders() {
        final int extruderCount = getPrinterSetting().getExtruderSettings().size();
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
        if (feedrate > xyFeedrate && Math.abs(extrudeLength) > MathRoutines.TINY_VALUE) {
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

    private static void checkCoordinates(final double x, final double y, final double z) {
        checkCoordinate("x", x, 0, getMaximumXvalue());
        checkCoordinate("y", y, 0, getMaximumYvalue());
        checkCoordinate("z", z, 0, getMaximumZvalue());
    }

    private static double checkCoordinate(final String name, final double value, final double minimumValue,
            final double maximumValue) {
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
            extruders[currentExtruder].stopExtruding();
        }
    }

    public void startRun(final Rectangle rectangle, final double machineZ, final double machineMaxZ) {
        gcode.writeComment(" GCode generated by RepRap Java Host Software");
        final Date myDate = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd:HH-mm-ss");
        final String myDateString = sdf.format(myDate);
        gcode.writeComment(" Created: " + myDateString);
        gcode.writeComment("#!RECTANGLE: " + rectangle + ", height: " + machineMaxZ);
        gcode.writeBlock(getPrinterSetting().getGcodePrologue(), "Prologue:", "------");
        currentX = 0;
        currentY = 0;
        currentZ = 0;
        currentFeedrate = -100; // Force it to set the feedrate at the start
        forceSelection = true; // Force it to set the extruder to use at the start

        if (getPrintSetting().printSkirt()) {
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
                    singleMove(currentX, currentY, getPrintSetting().getLayerHeight(), getFastFeedrateZ(), true);
                    currentZ = machineZ;
                }
                zRight = true;
                selectExtruder(e, true, false);
                final GCodeExtruder extruder = extruders[currentExtruder];
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
            final boolean really) {
        currentFeedrate = -1; // Force it to set the feedrate
        final String result;
        if (really) {
            gcode.writeComment("#!LAYER: " + machineLayer + "/" + (maxMachineLayer - 1));
            result = null;
        } else {
            final File temporaryFile = gcode.openTemporaryOutFile(machineLayer);
            result = temporaryFile.getPath();
        }
        extruders[currentExtruder].zeroExtrudedLength(really);
        currentZ = round(machineZ - zStep, 4);
        singleMove(getX(), getY(), machineZ, getFastFeedrateZ(), really);

        return result;
    }

    public void finishedLayer(final boolean reversing) {
        if (!reversing) {
            gcode.closeOutFile();
        }
    }

    public void terminate(final Point2D lastPoint, final double lastZ) {
        currentX = round(lastPoint.x(), 2);
        currentY = round(lastPoint.y(), 2);
        currentZ = round(lastZ, 1);

        gcode.writeBlock(getPrinterSetting().getGcodeEpilogue(), "Epilogue:", "------");
    }

    private static double round(final double c, final double d) {
        final double power = Math.pow(10.0, d);

        return Math.round(c * power) / power;
    }

    private void extrude(final double extrudeLength, final double feedRate) {
        final GCodeExtruder extruder = extruders[currentExtruder];

        final double scaledFeedRate;
        if (extruder.getFeedDiameter() > 0) {
            scaledFeedRate = feedRate * getPrintSetting().getLayerHeight() * extruder.getExtrusionSize()
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

    public void setGCodeFileForOutput(final File gcodeFile) {
        gcode.setGCodeFileForOutput(gcodeFile);
    }

    private void selectExtruder(final int materialIndex, final boolean really, final boolean update) {
        final GCodeExtruder oldExtruder = extruders[currentExtruder];
        final GCodeExtruder newExtruder = extruders[materialIndex];
        final boolean shield = getPrintSetting().printShield();

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
                final GCodeExtruder extruder = extruders[currentExtruder];
                extruder.stopExtruding(); // Make sure we are off

                if (shield) {
                    final Point2D purgePoint = purge.getPurgeEnd(extruder, true, 0);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();
                }

                // Now tell the GCodes to select the new extruder and stabilise all temperatures
                gcode.writeCommand("T" + newExtruder.getID(), "select new extruder");

                if (shield) {
                    // Plot the purge pattern with the new extruder
                    startExtruder(true);

                    Point2D purgePoint = purge.getPurgeEnd(extruder, false, 0);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(extruder, false, 1);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(extruder, true, 1);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(extruder, true, 2);
                    singleMove(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate(), true);
                    currentX = purgePoint.x();
                    currentY = purgePoint.y();

                    purgePoint = purge.getPurgeEnd(extruder, false, 2);
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

    public void startExtruder(final boolean firstOneInLayer) {
        final GCodeExtruder extruder = extruders[currentExtruder];
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
        final GCodeExtruder extruder = extruders[currentExtruder];
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
        return round(getPrinterSetting().getMaximumFeedrateZ(), 1);
    }

    /**
     * @return fast XY movement feedrate in mm/minute
     */
    public double getFastXYFeedrate() {
        return Math.min(getPrinterSetting().getMaximumFeedrateX(), getPrinterSetting().getMaximumFeedrateY());
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

    private static double getMaximumXvalue() {
        return getPrinterSetting().getBedSizeX();
    }

    private static double getMaximumYvalue() {
        return getPrinterSetting().getBedSizeY();
    }

    private static double getMaximumZvalue() {
        return getPrinterSetting().getMaximumZ();
    }

    private static boolean useRelativeExtrusion() {
        return getPrinterSetting().useRelativeDistanceE();
    }

    private static PrintSetting getPrintSetting() {
        return Configuration.getInstance().getCurrentConfiguration().getPrintSetting();
    }

    private static PrinterSetting getPrinterSetting() {
        return Configuration.getInstance().getCurrentConfiguration().getPrinterSetting();
    }
}
