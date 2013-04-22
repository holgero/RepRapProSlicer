package org.reprap.geometry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Preferences;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.polygons.BooleanGrid;
import org.reprap.geometry.polygons.CSG2D;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.BoundingBox;

/**
 * This stores a set of facts about the layer currently being made, and the
 * rules for such things as infill patterns, support patterns etc.
 */
public class LayerRules {
    private static final Logger LOGGER = LogManager.getLogger(LayerRules.class);
    /**
     * The coordinates of the first point plotted in a layer
     */
    private final Point2D[] firstPoint;

    /**
     * The extruder first used in a layer
     */
    private final int[] firstExtruder;

    /**
     * The coordinates of the last point plotted in a layer
     */
    private final Point2D[] lastPoint;

    /**
     * The extruder last used in a layer
     */
    private final int[] lastExtruder;

    /**
     * Record extruder usage in each layer for planning
     */
    private final boolean[][] extruderUsedThisLayer;

    /**
     * The heights of the layers
     */
    private final double[] layerZ;

    /**
     * The names of all the files for all the layers
     */
    private final String[] layerFileNames;
    /**
     * The machine
     */
    private final GCodePrinter printer;

    /**
     * How far up the model we are in mm
     */
    private double modelZ;

    /**
     * How far we are up from machine Z=0
     */
    private double machineZ;

    /**
     * The count of layers up the model
     */
    private int modelLayer;

    /**
     * The number of layers the machine has done
     */
    private int machineLayer;

    /**
     * The top of the model in model coordinates
     */
    private final double modelZMax;

    /**
     * The highest the machine should go this build
     */
    private final double machineZMax;

    /**
     * The number of the last model layer (first = 0)
     */
    private final int modelLayerMax;

    /**
     * The number of the last machine layer (first = 0)
     */
    private final int machineLayerMax;

    /**
     * Putting down foundations?
     */
    private boolean layingSupport = true;
    /**
     * The smallest step height of all the extruders
     */
    private double zStep;

    /**
     * The biggest step height of all the extruders
     */
    private double thickestZStep;

    /**
     * This is true until it is first read, when it becomes false
     */
    private boolean notStartedYet = true;
    /**
     * The XY rectangle that bounds the build
     */
    private final Rectangle bBox;

    /**
     * The maximum number of surface layers requested by any extruder
     */
    private int maxSurfaceLayers = 2;

    /**
     * How many physical extruders?
     */
    private int maxAddress = -1;

    private final Preferences preferences = Preferences.getInstance();

    LayerRules(final GCodePrinter printer, final BoundingBox box) {
        this.printer = printer;

        // Run through the extruders checking their layer heights and the
        // Actual physical extruder used.
        final GCodeExtruder[] es = printer.getExtruders();
        zStep = es[0].getExtrusionHeight();
        thickestZStep = zStep;
        int fineLayers = es[0].getLowerFineLayers();
        if (es.length > 1) {
            for (int i = 1; i < es.length; i++) {
                if (es[i].getLowerFineLayers() > fineLayers) {
                    fineLayers = es[i].getLowerFineLayers();
                }
                if (es[i].getExtrusionHeight() > thickestZStep) {
                    thickestZStep = es[i].getExtrusionHeight();
                }
                if (es[i].getExtrusionHeight() < zStep) {
                    zStep = es[i].getExtrusionHeight();
                }
                if (es[i].getSurfaceLayers() > maxSurfaceLayers) {
                    maxSurfaceLayers = es[i].getSurfaceLayers();
                }
                if (es[i].getPhysicalExtruderNumber() > maxAddress) {
                    maxAddress = es[i].getPhysicalExtruderNumber();
                }
            }
        }

        final long thick = Math.round(thickestZStep * 1000.0);
        for (int i = 0; i < es.length; i++) {
            final long thin = Math.round(es[i].getExtrusionHeight() * 1000.0);
            if (thick % thin != 0) {
                throw new RuntimeException("the layer height for extruder " + i + "(" + es[i].getLowerFineLayers()
                        + ") is not an integer divisor of the layer height for layer height " + thickestZStep);
            }
        }

        modelZMax = box.getZint().high();
        final int foundationLayers = Math.max(0, printer.getFoundationLayers());
        modelLayerMax = (int) (modelZMax / zStep) + 1;
        machineLayerMax = modelLayerMax + foundationLayers;
        machineZMax = modelZMax + foundationLayers * zStep;
        modelZ = modelZMax;
        machineZ = machineZMax;
        modelLayer = modelLayerMax;
        machineLayer = machineLayerMax;

        // Set up the records of the layers for later reversing (top->down ==>> bottom->up)
        firstPoint = new Point2D[machineLayerMax + 1];
        firstExtruder = new int[machineLayerMax + 1];
        lastPoint = new Point2D[machineLayerMax + 1];
        lastExtruder = new int[machineLayerMax + 1];
        layerZ = new double[machineLayerMax + 1];
        layerFileNames = new String[machineLayerMax + 1];
        extruderUsedThisLayer = new boolean[machineLayerMax + 1][maxAddress];
        for (int i = 0; i < machineLayerMax + 1; i++) {
            layerFileNames[i] = null;
            for (int j = 0; j < maxAddress; j++) {
                extruderUsedThisLayer[i][j] = false;
            }
        }

        final Rectangle gp = box.getXYbox();
        bBox = new Rectangle(new Point2D(gp.x().low() - 6, gp.y().low() - 6), new Point2D(gp.x().high() + 6, gp.y().high() + 6));
    }

    Rectangle getBox() {
        return bBox;
    }

    public GCodePrinter getPrinter() {
        return printer;
    }

    double getModelZ() {
        return modelZ;
    }

    public double getModelZ(final int layer) {
        return zStep * layer;
    }

    public double getMachineZ() {
        return machineZ;
    }

    public int getModelLayer() {
        return modelLayer;
    }

    /**
     * MIGHT an extruder be used in this layer. I.e. is this layer the correct
     * multiple of the microlayering heights for this extruder possibly to be
     * required.
     */
    public boolean extruderLiveThisLayer(final int e) {
        final GCodeExtruder[] es = printer.getExtruders();
        final double myHeight = es[e].getExtrusionHeight();
        final double eFraction = machineZ / myHeight;
        double delta = eFraction - Math.floor(eFraction);
        if (delta > 0.5) {
            delta = Math.ceil(eFraction) - eFraction;
        }
        delta = myHeight * delta;
        return (delta < zStep * 0.5);
    }

    public int sliceCacheSize() {
        return (int) Math.ceil(2 * (maxSurfaceLayers * 2 + 1) * thickestZStep / zStep);
    }

    void setFirstAndLast(final PolygonList[] pl) {
        firstPoint[machineLayer] = null;
        lastPoint[machineLayer] = null;
        firstExtruder[machineLayer] = -1;
        lastExtruder[machineLayer] = -1;
        layerZ[machineLayer] = machineZ;
        if (pl == null) {
            return;
        }
        if (pl.length <= 0) {
            return;
        }
        int bottom = -1;
        int top = -1;
        for (int i = 0; i < pl.length; i++) {
            if (pl[i] != null) {
                if (pl[i].size() > 0) {
                    if (bottom < 0) {
                        bottom = i;
                    }
                    top = i;
                }
            }
        }
        if (bottom < 0) {
            return;
        }
        firstPoint[machineLayer] = pl[bottom].polygon(0).point(0);
        pl[bottom].polygon(0).getAttributes();
        firstExtruder[machineLayer] = printer.getExtruder(pl[bottom].polygon(0).getAttributes().getMaterial()).getID();
        lastPoint[machineLayer] = pl[top].polygon(pl[top].size() - 1).point(pl[top].polygon(pl[top].size() - 1).size() - 1);
        pl[top].polygon(pl[top].size() - 1).getAttributes();
        lastExtruder[machineLayer] = printer.getExtruder(pl[top].polygon(pl[top].size() - 1).getAttributes().getMaterial())
                .getID();
    }

    public int realTopLayer() {
        int rtl = machineLayerMax;
        while (firstPoint[rtl] == null && rtl > 0) {
            final String message = "layer " + rtl + " from " + machineLayerMax + " is empty!";
            if (machineLayerMax - rtl > 1) {
                LOGGER.error(message);
            } else {
                LOGGER.debug(message);
            }
            rtl--;
        }
        return rtl;
    }

    public void setLayerFileName(final String name) {
        layerFileNames[machineLayer] = name;
    }

    private Point2D getFirstPoint(final int layer) {
        return firstPoint[layer];
    }

    public Point2D getLastPoint(final int layer) {
        return lastPoint[layer];
    }

    public double getLayerZ(final int layer) {
        return layerZ[layer];
    }

    public int getMachineLayerMax() {
        return machineLayerMax;
    }

    public int getMachineLayer() {
        return machineLayer;
    }

    int getFoundationLayers() {
        return machineLayerMax - modelLayerMax;
    }

    public double getZStep() {
        return zStep;
    }

    public boolean notStartedYet() {
        if (notStartedYet) {
            notStartedYet = false;
            return true;
        }
        return false;
    }

    void setLayingSupport(final boolean lf) {
        layingSupport = lf;
    }

    public boolean getLayingSupport() {
        return layingSupport;
    }

    /**
     * The hatch pattern is:
     * 
     * Foundation: X and Y rectangle
     * 
     * Model: Alternate even then odd (which can be set to the same angle if
     * wanted).
     */
    public HalfPlane getHatchDirection(final GCodeExtruder e, final boolean support) {
        final double myHeight = e.getExtrusionHeight();
        final double eFraction = machineZ / myHeight;
        final int mylayer = (int) Math.round(eFraction);

        double angle;

        if (getMachineLayer() < getFoundationLayers()) {
            angle = e.getOddHatchDirection();
        } else {
            if (mylayer % 2 == 0) {
                angle = e.getEvenHatchDirection();
            } else {
                angle = e.getOddHatchDirection();
            }
        }
        angle = angle * Math.PI / 180;
        HalfPlane result = new HalfPlane(new Point2D(0.0, 0.0), new Point2D(Math.sin(angle), Math.cos(angle)));

        if (((mylayer / 2) % 2 == 0) && !support) {
            result = result.offset(0.5 * getHatchWidth(e));
        }

        return result;
    }

    /**
     * The gap in the layer zig-zag is:
     * 
     * Foundation: The foundation width for all but... ...the penultimate
     * foundation layer, which is half that and.. ...the last foundation layer,
     * which is the model fill width
     * 
     * Model: The model fill width
     */
    public double getHatchWidth(final GCodeExtruder e) {
        if (getMachineLayer() < getFoundationLayers()) {
            return e.getExtrusionFoundationWidth();
        }

        return e.getExtrusionInfillWidth();
    }

    /**
     * Move the machine up/down, but leave the model's layer where it is.
     */
    void stepMachine() {
        machineLayer--;
        machineZ = zStep * machineLayer;
    }

    /**
     * Move both the model and the machine up/down a layer
     */
    void step() {
        modelLayer--;
        modelZ = modelLayer * zStep;
        stepMachine();
    }

    private void copyFile(final PrintStream ps, final String ip) throws IOException {
        final File f = new File(ip);
        final FileReader fr = new FileReader(f);
        try {
            int character;
            while ((character = fr.read()) >= 0) {
                ps.print((char) character);
            }
            ps.flush();
        } finally {
            fr.close();
        }
    }

    void reverseLayers() throws IOException {
        final String fileName = printer.getOutputFilename();
        final FileOutputStream fileStream = new FileOutputStream(fileName);
        try {
            final PrintStream fileOutStream = new PrintStream(fileStream);
            printer.forceOutputFile(fileOutStream);
            printer.startRun(bBox, machineZ, machineZMax); // Sets current X, Y, Z to 0 and optionally plots an outline
            final int top = realTopLayer();
            for (machineLayer = 1; machineLayer <= top; machineLayer++) {
                machineZ = layerZ[machineLayer];
                printer.startingLayer(zStep, machineZ, machineLayer, machineLayerMax, true);
                printer.singleMove(getFirstPoint(machineLayer).x(), getFirstPoint(machineLayer).y(), machineZ,
                        printer.getFastXYFeedrate(), true);
                copyFile(fileOutStream, layerFileNames[machineLayer]);

                if (preferences.loadBool("RepRapAccelerations")) {
                    printer.singleMove(getLastPoint(machineLayer).x(), getLastPoint(machineLayer).y(), machineZ,
                            printer.getSlowXYFeedrate(), false);
                } else {
                    printer.singleMove(getLastPoint(machineLayer).x(), getLastPoint(machineLayer).y(), machineZ,
                            printer.getFastXYFeedrate(), false);
                }
                printer.finishedLayer(true);
            }
            printer.terminate(lastPoint[realTopLayer()], layerZ[realTopLayer()]);
        } finally {
            fileStream.close();
        }
    }

    void layFoundationTopDown(final SimulationPlotter simulationPlot) throws IOException {
        if (getFoundationLayers() <= 0) {
            return;
        }

        setLayingSupport(true);
        printer.setSeparating(false);
        while (machineLayer >= 0) {
            LOGGER.debug("Commencing foundation layer at " + getMachineZ());
            setLayerFileName(printer.startingLayer(zStep, machineZ, machineLayer, machineLayerMax, false));
            fillFoundationRectangle(simulationPlot);
            printer.finishedLayer(false);
            stepMachine();
        }
    }

    private void fillFoundationRectangle(final SimulationPlotter simulationPlot) throws IOException {
        final PolygonList shield = new PolygonList();
        final GCodeExtruder e = printer.getExtruder();
        final Attributes fa = new Attributes(e.getMaterial(), null, e.getAppearance());
        final CSG2D rect = CSG2D.RrCSGFromBox(bBox);
        final BooleanGrid bg = new BooleanGrid(rect, bBox.scale(1.1), fa);
        final PolygonList h[] = { shield, bg.hatch(getHatchDirection(e, false), getHatchWidth(e), bg.attribute()) };
        setFirstAndLast(h);
        final LayerProducer lp = new LayerProducer(h, this, simulationPlot);
        lp.plot();
        printer.getExtruder().stopExtruding();
    }

}
