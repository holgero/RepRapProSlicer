package org.reprap.gcode;

/*
 * TODO: fixup warmup segments GCode (forgets to turn on extruder) 
 * TODO: fixup all the RR: println commands 
 * TODO: find a better place for the code. You cannot even detect a layer change without hacking now. 
 * TODO: read Zach's GCode examples to check if I messed up. 
 * TODO: make GCodeWriter a subclass of NullCartesian, so I don't have to fix code all over the place.
 */

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
import org.reprap.geometry.LayerRules;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polygons.VelocityProfile;
import org.reprap.geometry.polyhedra.Attributes;

public class GCodePrinter implements PreferenceChangeListener {
    private static final Logger LOGGER = LogManager.getLogger(GCodePrinter.class);
    private final GCodeWriter gcode = new GCodeWriter();
    private LayerRules layerRules = null;
    /**
     * Force an extruder to be selected on startup
     */
    private boolean forceSelection = true;
    private boolean XYEAtZero = false;
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
     * The speed from which the machine can do a standing start
     */
    private double slowXYFeedrate;
    /**
     * The fastest the machine can accelerate in Z
     */
    private double maxZAcceleration;
    /**
     * The speed from which the machine can do a standing start in Z
     */
    private double slowZFeedrate;
    /**
     * Feedrate for fast Z moves on the machine.
     */
    private double fastFeedrateZ;
    /**
     * Array containing the extruders on the 3D printer
     */
    private GCodeExtruder extruders[];
    private int currentExtruder;
    private long startedCooling = -1L;
    private int foundationLayers = 0;
    /**
     * The maximum X and Y point we can move to
     */
    private Point2D bedNorthEast;
    private boolean reprapAccelerations;
    private double maximumXvalue;
    private double maximumYvalue;
    private double maximumZvalue;
    private boolean relativeExtrusion;
    private final Preferences preferences = Preferences.getInstance();

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

    private void qFeedrate(final double feedrate) throws IOException {
        if (currentFeedrate == feedrate) {
            return;
        }
        gcode.writeCommand("G1 F" + feedrate, "feed for start of next move");
        currentFeedrate = feedrate;
    }

    private void qXYMove(final double x, final double y, double feedrate) throws Exception {
        final double dx = x - currentX;
        final double dy = y - currentY;

        double extrudeLength = extruders[currentExtruder].getDistance(Math.sqrt(dx * dx + dy * dy));
        String se = "";

        if (extrudeLength > 0) {
            if (extruders[currentExtruder].getReversing()) {
                extrudeLength = -extrudeLength;
            }
            extruders[currentExtruder].getExtruderState().add(extrudeLength);
            if (extruders[currentExtruder].get5D()) {
                if (relativeExtrusion) {
                    se = " E" + round(extrudeLength, 3);
                } else {
                    se = " E" + round(extruders[currentExtruder].getExtruderState().length(), 3);
                }
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

    private void qZMove(final double z, double feedrate) throws Exception {
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
            if (extruders[currentExtruder].get5D()) {
                if (relativeExtrusion) {
                    s += " E" + round(extrudeLength, 3);
                } else {
                    s += " E" + round(extruders[currentExtruder].getExtruderState().length(), 3);
                }
            }
        }
        if (currentFeedrate != feedrate) {
            s += " F" + feedrate;
            currentFeedrate = feedrate;
        }
        gcode.writeCommand(s, "z move");
        currentZ = z;
    }

    public void moveTo(double x, double y, double z, double feedrate, final boolean startUp, final boolean endUp)
            throws Exception {
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

        final double zFeedrate = round(getMaxFeedrateZ(), 1);

        final double liftIncrement = extruders[currentExtruder].getLift(); //extruders[extruder].getExtrusionHeight()/2;
        final double liftedZ = round(currentZ + liftIncrement, 4);

        //go up first?
        if (startUp) {
            qZMove(liftedZ, zFeedrate);
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

        if (endUp && !startUp) {
            qZMove(liftedZ, zFeedrate);
            qFeedrate(feedrate);
        }

        if (!endUp && startUp) {
            qZMove(liftedZ - liftIncrement, zFeedrate);
            qFeedrate(feedrate);
        }

        checkCoordinates(x, y, z);

        currentX = x;
        currentY = y;
        currentZ = z;
        XYEAtZero = false;
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

        try {
            if (!reprapAccelerations) {
                moveTo(x, y, z, feedrate, false, false);
                return;
            }
        } catch (final Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        try {
            if (xyMove && getExtruder().getMaxAcceleration() <= 0) {
                moveTo(x, y, z, feedrate, false, false);
                return;
            }

            if (xyMove) {
                final double s = Math.sqrt(dx * dx + dy * dy);

                final VelocityProfile vp = new VelocityProfile(s, getExtruder().getSlowXYFeedrate(), feedrate, getExtruder()
                        .getSlowXYFeedrate(), getExtruder().getMaxAcceleration());
                switch (vp.flat()) {
                case 0:
                    qFeedrate(feedrate);
                    moveTo(x, y, z0, feedrate, false, false);
                    break;

                case 1:
                    qFeedrate(getExtruder().getSlowXYFeedrate());
                    moveTo(x0 + dx * vp.s1() / s, y0 + dy * vp.s1() / s, z0, vp.v(), false, false);
                    moveTo(x, y, z0, getExtruder().getSlowXYFeedrate(), false, false);
                    break;

                case 2:
                    qFeedrate(getExtruder().getSlowXYFeedrate());
                    moveTo(x0 + dx * vp.s1() / s, y0 + dy * vp.s1() / s, z0, feedrate, false, false);
                    moveTo(x0 + dx * vp.s2() / s, y0 + dy * vp.s2() / s, z0, feedrate, false, false);
                    moveTo(x, y, z0, getExtruder().getSlowXYFeedrate(), false, false);
                    break;

                default:
                    LOGGER.error("GCodeRepRap.singleMove(): dud VelocityProfile XY flat value.");
                }
            }

            if (zMove) {
                final VelocityProfile vp = new VelocityProfile(Math.abs(dz), getSlowZFeedrate(), feedrate, getSlowZFeedrate(),
                        getMaxZAcceleration());
                double s = 1;
                if (dz < 0) {
                    s = -1;
                }
                switch (vp.flat()) {
                case 0:
                    qFeedrate(feedrate);
                    moveTo(x0, y0, z, feedrate, false, false);
                    break;

                case 1:
                    qFeedrate(getSlowZFeedrate());
                    moveTo(x0, y0, z0 + s * vp.s1(), vp.v(), false, false);
                    moveTo(x0, y0, z, getSlowZFeedrate(), false, false);
                    break;

                case 2:
                    qFeedrate(getSlowZFeedrate());
                    moveTo(x0, y0, z0 + s * vp.s1(), feedrate, false, false);
                    moveTo(x0, y0, z0 + s * vp.s2(), feedrate, false, false);
                    moveTo(x0, y0, z, getSlowZFeedrate(), false, false);
                    break;

                default:
                    LOGGER.error("GCodeRepRap.singleMove(): dud VelocityProfile Z flat value.");
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.toString());
        }
    }

    public void printTo(final double x, final double y, final double z, final double feedrate, final boolean stopExtruder,
            final boolean closeValve) throws Exception {
        moveTo(x, y, z, feedrate, false, false);

        if (stopExtruder) {
            getExtruder().stopExtruding();
        }
        if (closeValve) {
            getExtruder().setValve(false);
        }
    }

    public void startRun(final LayerRules lc) throws IOException {
        gcode.writeComment(" GCode generated by RepRap Java Host Software");
        final Date myDate = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd:HH-mm-ss");
        final String myDateString = sdf.format(myDate);
        gcode.writeComment(" Created: " + myDateString);
        gcode.writeComment("#!RECTANGLE: " + lc.getBox() + ", height: " + lc.getMachineZMAx());
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
            plotOutlines(lc);
        }
    }

    /**
     * Plot rectangles round the build on layer 0 or above
     * 
     * @param lc
     */
    private void plotOutlines(final LayerRules lc) {
        boolean zRight = false;
        try {
            Rectangle r = lc.getBox();

            for (int e = extruders.length - 1; e >= 0; e--) // Count down so we end with the one most likely to start the rest
            {
                final int pe = extruders[e].getPhysicalExtruderNumber();
                if (physicalExtruderUsed[pe]) {
                    if (!zRight) {
                        singleMove(currentX, currentY, getExtruder().getExtrusionHeight(), getFastFeedrateZ(), true);
                        currentZ = lc.getMachineZ();
                    }
                    zRight = true;
                    selectExtruder(e, true, false);
                    singleMove(r.x().low(), r.y().low(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    printStartDelay(true);
                    getExtruder().setExtrusion(getExtruder().getExtruderSpeed(), false);
                    singleMove(r.x().high(), r.y().low(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    singleMove(r.x().high(), r.y().high(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    singleMove(r.x().low(), r.y().high(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    singleMove(r.x().low(), r.y().low(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = r.x().low();
                    currentY = r.y().low();
                    printEndReverse();
                    getExtruder().stopExtruding();
                    getExtruder().setValve(false);
                    r = r.offset(2 * getExtruder().getExtrusionSize());
                    physicalExtruderUsed[pe] = false; // Stop us doing it again
                }
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void startingLayer(final LayerRules lc) throws IOException {
        currentFeedrate = -1; // Force it to set the feedrate
        gcode.startingLayer(lc);
        if (lc.getReversing()) {
            gcode.writeComment("#!LAYER: " + (lc.getMachineLayer()) + "/" + (lc.getMachineLayerMax() - 1));
        }

        // Don't home the first layer
        // The startup procedure has already done that
        if (lc.getMachineLayer() > 0 && preferences.loadBool("InterLayerCooling")) {
            double liftZ = -1;
            for (final GCodeExtruder extruder2 : extruders) {
                if (extruder2.getLift() > liftZ) {
                    liftZ = extruder2.getLift();
                }
            }
            if (liftZ > 0) {
                singleMove(getX(), getY(), currentZ + liftZ, getFastFeedrateZ(), lc.getReversing());
            }
            homeToZeroXYE(lc.getReversing());
            if (liftZ > 0) {
                singleMove(getX(), getY(), currentZ, getFastFeedrateZ(), lc.getReversing());
            }
        } else {
            getExtruder().zeroExtrudedLength(lc.getReversing());
        }

        if (layerPauseCheckbox != null && layerPauseCheckbox.isSelected()) {
            layerPause();
        }

        final double coolDuration = getExtruder().getCoolingPeriod() * 1000L;
        if (startedCooling >= 0) {
            final long fanHasBeenOnDuration = System.currentTimeMillis() - startedCooling;
            machineWait((long) coolDuration - fanHasBeenOnDuration, false, lc.getReversing());
        }
        if (coolDuration > 0) {
            // Fan off
            getExtruder().setCooler(false, lc.getReversing());
        }

        lc.moveZAtStartOfLayer(lc.getReversing());
    }

    public void finishedLayer(final LayerRules lc) throws IOException {
        final double coolTime = getExtruder().getCoolingPeriod();
        startedCooling = -1;

        if (coolTime > 0 && !lc.notStartedYet()) {
            getExtruder().setCooler(true, lc.getReversing());
            LOGGER.debug("Start of cooling period");
            // Go home. Seek (0,0) then callibrate X first
            homeToZeroXYE(lc.getReversing());
            startedCooling = System.currentTimeMillis();
        }
        gcode.finishedLayer(lc);
    }

    public void terminate(final LayerRules lc) throws IOException {
        final int topLayer = lc.realTopLayer();
        final Point2D p = lc.getLastPoint(topLayer);
        currentX = round(p.x(), 2);
        currentY = round(p.y(), 2);
        currentZ = round(lc.getLayerZ(topLayer), 1);

        final boolean debugGcode = preferences.loadBool("Debug");
        if (debugGcode) {
            gcode.writeComment(" Epilogue:");
        }
        gcode.copyFile(preferences.getEpilogueFile());
        if (debugGcode) {
            gcode.writeComment(" ------");
        }
    }

    private void delay(final long millis, final boolean fastExtrude, final boolean really) throws IOException {
        double extrudeLength = getExtruder().getDistanceFromTime(millis);

        if (extrudeLength > 0) {
            if (extruders[currentExtruder].get5D()) {
                double fr;
                if (fastExtrude) {
                    fr = getExtruder().getFastEFeedrate();
                } else {
                    fr = getExtruder().getFastXYFeedrate();
                }

                currentFeedrate = fr;
                // Fix the value for possible feedrate change
                extrudeLength = getExtruder().getDistanceFromTime(millis);

                if (getExtruder().getFeedDiameter() > 0) {
                    fr = fr * getExtruder().getExtrusionHeight() * getExtruder().getExtrusionSize()
                            / (getExtruder().getFeedDiameter() * getExtruder().getFeedDiameter() * Math.PI / 4);
                }

                fr = round(fr, 1);

                if (really) {
                    currentFeedrate = 0; // force it to output feedrate
                    qFeedrate(fr);
                }
            }

            if (extruders[currentExtruder].getReversing()) {
                extrudeLength = -extrudeLength;
            }

            extruders[currentExtruder].getExtruderState().add(extrudeLength);

            if (extruders[currentExtruder].get5D()) {
                final double fr;
                if (reprapAccelerations) {
                    fr = getExtruder().getSlowXYFeedrate();
                } else {
                    fr = getExtruder().getFastXYFeedrate();
                }

                if (really) {
                    final String command;

                    if (relativeExtrusion) {
                        command = "G1 E" + round(extrudeLength, 3);
                    } else {
                        command = "G1 E" + round(extruders[currentExtruder].getExtruderState().length(), 3);
                    }

                    final String comment;
                    if (extruders[currentExtruder].getReversing()) {
                        comment = "extruder retraction";
                    } else {
                        comment = "extruder dwell";
                    }
                    gcode.writeCommand(command, comment);
                    qFeedrate(fr);
                } else {
                    currentFeedrate = fr;
                }
                return;
            }
        }

        gcode.writeCommand("G4 P" + millis, "delay");
    }

    private void homeToZeroX() throws IOException {
        gcode.writeCommand("G28 X0", "set x 0");
        currentX = 0.0;
    }

    private void homeToZeroY() throws IOException {
        gcode.writeCommand("G28 Y0", "set y 0");
        currentY = 0.0;
    }

    private void homeToZeroXYE(final boolean really) throws IOException {
        if (XYEAtZero) {
            return;
        }
        if (really) {
            homeToZeroX();
            homeToZeroY();
        } else {
            currentX = 0;
            currentY = 0;
        }
        final int extruderNow = currentExtruder;
        for (int i = 0; i < extruders.length; i++) {
            selectExtruder(i, really, false);
            extruders[i].zeroExtrudedLength(really);
        }
        selectExtruder(extruderNow, really, false);
        XYEAtZero = true;
    }

    private static double round(final double c, final double d) {
        final double power = Math.pow(10.0, d);

        return Math.round(c * power) / power;
    }

    /**
     * All machine dwells and delays are routed via this function, rather than
     * calling Thread.sleep - this allows them to generate the right G codes
     * (G4) etc.
     */
    private void machineWait(final double milliseconds, final boolean fastExtrude, final boolean really) throws IOException {
        if (milliseconds <= 0) {
            return;
        }
        delay((long) milliseconds, fastExtrude, really);
    }

    public String setGCodeFileForOutput(final String fileRoot) {
        return gcode.setGCodeFileForOutput(fileRoot);
    }

    /**
     * Tell the printer class it's Z position. Only to be used if you know what
     * you're doing...
     */
    public void setZ(final double z) {
        currentZ = round(z, 4);
    }

    private void selectExtruder(final int materialIndex, final boolean really, final boolean update) throws IOException {
        final int oldPhysicalExtruder = getExtruder().getPhysicalExtruderNumber();
        final GCodeExtruder oldExtruder = getExtruder();
        final int newPhysicalExtruder = extruders[materialIndex].getPhysicalExtruderNumber();
        final boolean shield = preferences.loadBool("Shield");
        Point2D purge;

        if (newPhysicalExtruder != oldPhysicalExtruder || forceSelection) {
            if (really) {
                oldExtruder.stopExtruding();

                if (!false) {
                    if (materialIndex < 0 || materialIndex >= extruders.length) {
                        LOGGER.error("Selected material (" + materialIndex + ") is out of range.");
                        currentExtruder = 0;
                    } else {
                        currentExtruder = materialIndex;
                    }
                }

                if (update) {
                    physicalExtruderUsed[newPhysicalExtruder] = true;
                }
                getExtruder().stopExtruding(); // Make sure we are off

                if (shield) {
                    purge = layerRules.getPurgeEnd(true, 0);
                    singleMove(purge.x(), purge.y(), currentZ, getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();
                }

                // Now tell the GCodes to select the new extruder and stabilise all temperatures
                gcode.writeCommand("T" + newPhysicalExtruder, "select new extruder");

                if (shield) {
                    // Plot the purge pattern with the new extruder
                    printStartDelay(true);
                    getExtruder().setExtrusion(getExtruder().getExtruderSpeed(), false);

                    purge = layerRules.getPurgeEnd(false, 0);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(false, 1);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(true, 1);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(true, 2);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    purge = layerRules.getPurgeEnd(false, 2);
                    singleMove(purge.x(), purge.y(), currentZ, getExtruder().getFastXYFeedrate(), true);
                    currentX = purge.x();
                    currentY = purge.y();

                    printEndReverse();
                    getExtruder().stopExtruding();
                    getExtruder().setValve(false);
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

    public void setLayerRules(final LayerRules l) {
        layerRules = l;
    }

    public void selectExtruder(final Attributes att) throws Exception {
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

    /**
     * Extrude for the given time in milliseconds, so that polymer is flowing
     * before we try to move the extruder. But first take up the slack from any
     * previous reverse.
     */
    public void printStartDelay(final boolean firstOneInLayer) throws IOException {
        final double rDelay = getExtruder().getExtruderState().retraction();

        if (rDelay > 0) {
            getExtruder().setMotor(true);
            machineWait(rDelay, true, true);
            getExtruder().getExtruderState().setRetraction(0);
        }

        // Extrude motor and valve delays (ms)
        double eDelay, vDelay;

        if (firstOneInLayer) {
            eDelay = getExtruder().getExtrusionDelayForLayer();
            vDelay = getExtruder().getValveDelayForLayer();
        } else {
            eDelay = getExtruder().getExtrusionDelayForPolygon();
            vDelay = getExtruder().getValveDelayForPolygon();
        }

        if (eDelay >= vDelay) {
            getExtruder().setMotor(true);
            machineWait(eDelay - vDelay, false, true);
            getExtruder().setValve(true);
            machineWait(vDelay, false, true);
        } else {
            getExtruder().setValve(true);
            machineWait(vDelay - eDelay, false, true);
            getExtruder().setMotor(true);
            machineWait(eDelay, false, true);
        }
    }

    /**
     * Extrude backwards for the given time in milliseconds, so that polymer is
     * stopped flowing at the end of a track. Return the amount reversed.
     */
    public double printEndReverse() throws IOException {
        final double delay = getExtruder().getExtrusionReverseDelay();

        if (delay <= 0) {
            return 0;
        }

        getExtruder().setExtrusion(getExtruder().getExtruderSpeed(), true);
        machineWait(delay, true, true);
        getExtruder().stopExtruding();
        getExtruder().getExtruderState().setRetraction(getExtruder().getExtruderState().retraction() + delay);
        return getExtruder().getExtruderState().retraction();
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
     * Set all the extruders' separating mode
     */
    public void setSeparating(final boolean s) {
        for (final GCodeExtruder extruder2 : extruders) {
            extruder2.setSeparating(s);
        }
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
     * @return slow XY movement feedrate in mm/minute
     */
    public double getSlowXYFeedrate() {
        return slowXYFeedrate;
    }

    /**
     * @return the fastest the machine can accelerate
     */
    public double getMaxXYAcceleration() {
        return maxXYAcceleration;
    }

    private double getSlowZFeedrate() {
        return slowZFeedrate;
    }

    private double getMaxZAcceleration() {
        return maxZAcceleration;
    }

    public void forceNextExtruder() {
        forceSelection = true;
    }

    /**
     * Return the current layer rules
     */
    public LayerRules getLayerRules() {
        return layerRules;
    }

    /**
     * The XY point furthest from the origin
     */
    public Point2D getBedNorthEast() {
        return bedNorthEast;
    }

    @Override
    public void refreshPreferences(final Preferences prefs) {
        reprapAccelerations = prefs.loadBool("RepRapAccelerations");
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
        slowXYFeedrate = prefs.loadDouble("SlowXYFeedrate(mm/minute)");

        maxZAcceleration = prefs.loadDouble("MaxZAcceleration(mm/mininute/minute)");
        slowZFeedrate = prefs.loadDouble("SlowZFeedrate(mm/minute)");

        //set our standard feedrates.
        fastXYFeedrate = Math.min(maxFeedrateX, maxFeedrateY);
        setFastFeedrateZ(maxFeedrateZ);

        foundationLayers = prefs.loadInt("FoundationLayers");
        loadExtruders();
    }
}
