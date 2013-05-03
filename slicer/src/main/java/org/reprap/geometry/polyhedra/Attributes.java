package org.reprap.geometry.polyhedra;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Constants;
import org.reprap.configuration.MaterialSettings;
import org.reprap.configuration.Preferences;

/**
 * Holds RepRap attributes that are attached to Java3D shapes as user data,
 * primarily to record the material that things are made from.
 * 
 * @author adrian
 */
public class Attributes {
    private static final Logger LOGGER = LogManager.getLogger(Attributes.class);
    /**
     * The name of the material
     */
    private String material;

    /**
     * The STLObject of which this is a part
     */
    private final STLObject parent;

    /**
     * The appearance (colour) in the loading and simulation windows
     */
    private Appearance app;

    /**
     * Constructor - it is permissible to set any argument null. If you know
     * what you're doing of course...
     * 
     * @param s
     *            The name of the material
     * @param p
     *            Parent STLObject
     * @param a
     *            what it looks like
     */
    public Attributes(final String s, final STLObject p, final Appearance a) {
        material = s;
        parent = p;
        app = a;
    }

    @Override
    public String toString() {
        return "Attributes: material is " + material;
    }

    /**
     * @return the name of the material
     */
    public String getMaterial() {
        return material;
    }

    /**
     * @return the parent object
     */
    public STLObject getParent() {
        return parent;
    }

    /**
     * @return what colour am I?
     */
    public Appearance getAppearance() {
        return app;
    }

    /**
     * Change the material name
     */
    public void setMaterial(final String newMaterial) {
        material = existingMaterial(newMaterial);
        final MaterialSettings materialSettings = Preferences.getInstance().getMaterialSettings(material);
        final Color3f color = materialSettings.getColor();
        final Appearance appearance = new Appearance();
        appearance.setMaterial(new Material(color, Constants.BLACK, color, Constants.BLACK, 101f));
        app = appearance;
        if (parent != null) {
            parent.restoreAppearance();
        }
    }

    private static String existingMaterial(final String newMaterial) {
        final MaterialSettings materialSettings = Preferences.getInstance().getMaterialSettings(newMaterial);
        if (materialSettings != null) {
            return newMaterial;
        }
        final String substitute = Preferences.getInstance().getPrinterSettings().getExtruderSettings()[0].getMaterial()
                .getName();
        LOGGER.warn("Requested material " + newMaterial + " not found, substituting with " + substitute + ".");
        return substitute;
    }
}
