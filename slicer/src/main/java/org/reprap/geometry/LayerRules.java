package org.reprap.geometry;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.FillPattern;
import org.reprap.configuration.PrintSetting;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.PolygonList;
import org.reprap.geometry.polygons.Rectangle;
import org.reprap.geometry.polyhedra.BoundingBox;

/**
 * This stores a set of facts about the layer currently being made, and the
 * rules for such things as infill patterns, support patterns etc.
 */
public class LayerRules {
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

    private final CurrentConfiguration currentConfiguration;

    /**
     * The count of layers up the model
     */
    private int modelLayer;

    /**
     * The number of layers the machine has done
     */
    private int machineLayer;

    LayerRules(final BoundingBox box, final CurrentConfiguration currentConfiguration) {
        this.currentConfiguration = currentConfiguration;
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        zStep = printSetting.getLayerHeight();

        final double modelZMax = box.getZint().high();
        final int foundationLayers = Math.max(0, printSetting.getRaftLayers());
        modelLayerMax = (int) (modelZMax / zStep) + 1;
        machineLayerMax = modelLayerMax + foundationLayers;
        modelLayer = 0;
        machineLayer = 0;
        final Rectangle gp = box.getXYbox();
        bBox = new Rectangle(new Point2D(gp.x().low() - 6, gp.y().low() - 6), new Point2D(gp.x().high() + 6, gp.y().high() + 6));
    }

    Rectangle getBox() {
        return bBox;
    }

    double getModelZ(final int layer) {
        return zStep * (layer + 0.5);
    }

    double getMachineZ() {
        return (machineLayer + 1) * zStep;
    }

    int getModelLayer() {
        return modelLayer;
    }

    boolean setFirstAndLast(final PolygonList[] polygonLists) {
        if (polygonLists == null) {
            return false;
        }
        if (polygonLists.length <= 0) {
            return false;
        }
        Point2D first = null;
        PolygonList lastList = null;
        for (final PolygonList polygonList : polygonLists) {
            if (polygonList != null) {
                if (polygonList.size() > 0) {
                    if (first == null) {
                        first = polygonList.polygon(0).point(0);
                    }
                    lastList = polygonList;
                }
            }
        }
        if (lastList == null || first == null) {
            return false;
        }
        return true;
    }

    int getMachineLayerMax() {
        return machineLayerMax;
    }

    int getMachineLayer() {
        return machineLayer;
    }

    int getFoundationLayers() {
        return machineLayerMax - modelLayerMax;
    }

    double getZStep() {
        return zStep;
    }

    HalfPlane getFillHatchLine(final double alternatingOffset) {
        final int mylayer;
        if (getMachineLayer() < getFoundationLayers()) {
            mylayer = 1;
        } else {
            mylayer = machineLayer;
        }
        final FillPattern fillPattern = currentConfiguration.getPrintSetting().getFillPattern();
        HalfPlane result = getHatchLine(mylayer, fillPattern);

        if ((mylayer / 2) % 2 == 0) {
            result = result.offset(0.5 * alternatingOffset);
        }

        return result;
    }

    static HalfPlane getHatchLine(final int layer, final FillPattern fillPattern) {
        final double angle = Math.toRadians(fillPattern.angle(layer));
        return new HalfPlane(new Point2D(0.0, 0.0), new Point2D(Math.sin(angle), Math.cos(angle)));
    }

    /**
     * Move the machine up/down, but leave the model's layer where it is.
     */
    void stepMachine() {
        machineLayer++;
    }

    /**
     * Move both the model and the machine up/down a layer
     */
    void step() {
        modelLayer++;
        stepMachine();
    }
}
