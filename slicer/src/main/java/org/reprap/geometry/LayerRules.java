package org.reprap.geometry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Constants;
import org.reprap.configuration.FillPattern;
import org.reprap.configuration.MaterialSettings;
import org.reprap.configuration.Preferences;
import org.reprap.configuration.PrintSettings;
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
     * The smallest step height of all the extruders
     */
    private final double zStep;

    /**
     * The XY rectangle that bounds the build
     */
    private final Rectangle bBox;

    /**
     * The number of surface layers
     */
    private final int maxSurfaceLayers;

    private final Preferences preferences = Preferences.getInstance();

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

    LayerRules(final GCodePrinter printer, final BoundingBox box) {
        this.printer = printer;
        final PrintSettings printSettings = preferences.getPrintSettings();
        zStep = printSettings.getLayerHeight();
        maxSurfaceLayers = printSettings.getHorizontalShells();

        modelZMax = box.getZint().high();
        final int foundationLayers = Math.max(0, printSettings.getRaftLayers());
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
        for (int i = 0; i < machineLayerMax + 1; i++) {
            layerFileNames[i] = null;
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

    public int sliceCacheSize() {
        return (int) Math.ceil(2 * (maxSurfaceLayers * 2 + 1));
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

    public HalfPlane getHatchDirection(final boolean support, final double alternatingOffset) {
        final int mylayer;
        if (getMachineLayer() < getFoundationLayers()) {
            mylayer = 1;
        } else {
            mylayer = machineLayer;
        }
        final FillPattern fillPattern;
        if (support) {
            fillPattern = preferences.getPrintSettings().getSupportPattern();
        } else {
            fillPattern = preferences.getPrintSettings().getFillPattern();
        }
        final double angle = Math.toRadians(fillPattern.angle(mylayer));
        HalfPlane result = new HalfPlane(new Point2D(0.0, 0.0), new Point2D(Math.sin(angle), Math.cos(angle)));

        if (((mylayer / 2) % 2 == 0) && !support) {
            result = result.offset(0.5 * alternatingOffset);
        }

        return result;
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

                printer.singleMove(getLastPoint(machineLayer).x(), getLastPoint(machineLayer).y(), machineZ,
                        printer.getFastXYFeedrate(), false);
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
        final MaterialSettings material = e.getMaterial();
        final Appearance appearance = new Appearance();
        appearance.setMaterial(new Material(material.getColor(), Constants.BLACK, material.getColor(), Constants.BLACK, 101f));
        final Attributes fa = new Attributes(material.getName(), null, appearance);
        final CSG2D rect = CSG2D.RrCSGFromBox(bBox);
        final BooleanGrid bg = new BooleanGrid(rect, bBox.scale(1.1), fa);
        final PolygonList h[] = { shield,
                bg.hatch(getHatchDirection(false, e.getExtrusionSize()), e.getExtrusionSize(), bg.attribute()) };
        setFirstAndLast(h);
        final LayerProducer lp = new LayerProducer(h, this, simulationPlot);
        lp.plot();
        printer.getExtruder().stopExtruding();
    }

}
