package org.reprap.gcode;

import static org.reprap.configuration.store.MathRoutines.circleAreaForDiameter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.PrintSetting;
import org.reprap.configuration.PrinterSetting;
import org.reprap.configuration.store.MathRoutines;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Rectangle;

public class GCodePrinter {
    private static final Logger LOGGER = LogManager.getLogger(GCodePrinter.class);
    private final CurrentConfiguration currentConfiguration;
    private final GCodeWriter gcode;
    private final GCodeExtruder extruders[];
    private final Purge purge;
    /**
     * Force an extruder to be selected on startup
     */
    private boolean forceSelection = true;
    private double currentX = 0;
    private double currentY = 0;
    private double currentZ = 0;
    private double currentFeedrate = 0;
    private int currentExtruder;

    public GCodePrinter(final CurrentConfiguration currentConfiguration, final Purge purge) {
        this.currentConfiguration = currentConfiguration;
        this.purge = purge;
        gcode = new GCodeWriter(getPrintSetting().isVerboseGCode());
        gcode.writeCommand("M110", "reset the line numbers");
        final int extruderCount = getPrinterSetting().getExtruderSettings().size();
        if (extruderCount < 1) {
            throw new IllegalStateException("A Reprap printer must contain at least one extruder.");
        }
        extruders = new GCodeExtruder[extruderCount];
        currentExtruder = 0;
        createExtruders();
    }

    private void createExtruders() {
        for (int i = 0; i < extruders.length; i++) {
            extruders[i] = new GCodeExtruder(gcode, i, currentConfiguration);
        }
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

    public void moveTo(double x, double y, double z, double feedrate) {
        x = checkCoordinate("x", x, getMaximumXvalue());
        y = checkCoordinate("y", y, getMaximumYvalue());
        z = checkCoordinate("z", z, getMaximumZvalue());
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
            LOGGER.debug("attempt to move in X|Y and Z simultaneously: (x, y, z) = (" + currentX + "->" + x + ", " + currentY
                    + "->" + y + ", " + currentZ + "->" + z + ", " + ")");
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

        currentX = x;
        currentY = y;
        currentZ = z;
    }

    private static double checkCoordinate(final String name, final double value, final double maximumValue) {
        if (value > maximumValue || value < 0) {
            LOGGER.error("Attempt to move " + name + " to " + value + " which is outside [" + 0 + ", " + maximumValue + "]");
        }
        return Math.max(0, Math.min(value, maximumValue));
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
        moveTo(currentX, currentY, machineZ, getFastFeedrateZ());
        currentZ = machineZ;
        selectExtruder(0);
        final GCodeExtruder extruder = extruders[currentExtruder];
        moveTo(rectangle.x().low(), rectangle.y().low(), currentZ, extruder.getFastXYFeedrate());
        startExtruder(true);
        moveTo(rectangle.x().high(), rectangle.y().low(), currentZ, extruder.getFastXYFeedrate());
        moveTo(rectangle.x().high(), rectangle.y().high(), currentZ, extruder.getFastXYFeedrate());
        moveTo(rectangle.x().low(), rectangle.y().high(), currentZ, extruder.getFastXYFeedrate());
        moveTo(rectangle.x().low(), rectangle.y().low(), currentZ, extruder.getFastXYFeedrate());
        currentX = rectangle.x().low();
        currentY = rectangle.y().low();
        retract();
        rectangle = rectangle.offset(2 * extruder.getExtrusionSize());
    }

    public void startingLayer(final double zStep, final double machineZ, final int machineLayer, final int maxMachineLayer) {
        currentFeedrate = -1; // Force it to set the feedrate
        gcode.writeComment("#!LAYER: " + machineLayer + "/" + (maxMachineLayer - 1));
        extruders[currentExtruder].zeroExtrudedLength();
        currentZ = round(machineZ - zStep, 4);
        moveTo(currentX, currentY, machineZ, getFastFeedrateZ());
    }

    public void terminate() {
        if (getPrintSetting().printShield()) {
            moveToDump(extruders[currentExtruder]);
        }

        gcode.writeBlock(getPrinterSetting().getGcodeEpilogue(), "Epilogue:", "------");
        gcode.closeOutFile();
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

    private void selectExtruder(final int materialIndex) {
        final GCodeExtruder oldExtruder = extruders[currentExtruder];
        final GCodeExtruder newExtruder = extruders[materialIndex];
        final boolean shield = getPrintSetting().printShield();

        if (newExtruder != oldExtruder || forceSelection) {
            oldExtruder.stopExtruding();
            if (materialIndex < 0 || materialIndex >= extruders.length) {
                LOGGER.error("Selected material (" + materialIndex + ") is out of range.");
                currentExtruder = 0;
            } else {
                currentExtruder = materialIndex;
            }

            final GCodeExtruder extruder = extruders[currentExtruder];
            extruder.stopExtruding(); // Make sure we are off
            if (shield) {
                moveToDump(extruder);
            }
            gcode.writeCommand("T" + newExtruder.getID(), "select new extruder");
            if (shield) {
                plotPurgePattern(extruder);
            }
            forceSelection = false;
        }
    }

    private void plotPurgePattern(final GCodeExtruder extruder) {
        startExtruder(true);

        Point2D purgePoint = purge.getPurgeEnd(extruder, false, 0);
        moveTo(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate());
        currentX = purgePoint.x();
        currentY = purgePoint.y();

        purgePoint = purge.getPurgeEnd(extruder, false, 1);
        moveTo(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate());
        currentX = purgePoint.x();
        currentY = purgePoint.y();

        purgePoint = purge.getPurgeEnd(extruder, true, 1);
        moveTo(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate());
        currentX = purgePoint.x();
        currentY = purgePoint.y();

        purgePoint = purge.getPurgeEnd(extruder, true, 2);
        moveTo(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate());
        currentX = purgePoint.x();
        currentY = purgePoint.y();

        purgePoint = purge.getPurgeEnd(extruder, false, 2);
        moveTo(purgePoint.x(), purgePoint.y(), currentZ, extruder.getFastXYFeedrate());
        currentX = purgePoint.x();
        currentY = purgePoint.y();

        retract();
    }

    private void moveToDump(final GCodeExtruder extruder) {
        final Point2D purgePoint = purge.getPurgeEnd(extruder, true, 0);
        moveTo(purgePoint.x(), purgePoint.y(), currentZ, getFastXYFeedrate());
        currentX = purgePoint.x();
        currentY = purgePoint.y();
    }

    public void selectExtruder(final String material) {
        for (int i = 0; i < extruders.length; i++) {
            if (material.equals(extruders[i].getMaterial().getName())) {
                selectExtruder(i);
                return;
            }
        }
        LOGGER.error("selectExtruder() - extruder not found for: " + material);
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
            extruder.stopExtruding();
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

    private double getMaximumXvalue() {
        return getPrinterSetting().getBedSizeX();
    }

    private double getMaximumYvalue() {
        return getPrinterSetting().getBedSizeY();
    }

    private double getMaximumZvalue() {
        return getPrinterSetting().getMaximumZ();
    }

    private boolean useRelativeExtrusion() {
        return getPrinterSetting().useRelativeDistanceE();
    }

    private PrintSetting getPrintSetting() {
        return currentConfiguration.getPrintSetting();
    }

    private PrinterSetting getPrinterSetting() {
        return currentConfiguration.getPrinterSetting();
    }

    public void travelTo(final double x, final double y) {
        final double liftZ = extruders[currentExtruder].getLift();
        if (liftZ > 0) {
            moveTo(getX(), getY(), currentZ + liftZ, getFastFeedrateZ());
        }
        moveTo(x, y, currentZ, getFastXYFeedrate());
        if (liftZ > 0) {
            moveTo(x, y, currentZ - liftZ, getFastFeedrateZ());
        }
    }
}
