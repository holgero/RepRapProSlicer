package org.reprap.geometry.polyhedra;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Constants;
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
        app = getAppearanceFromMaterial(material);
        if (parent != null) {
            parent.restoreAppearance();
        }
    }

    private static String existingMaterial(final String newMaterial) {
        final String[] materials = Preferences.getInstance().getAllMaterials();
        for (final String existingMaterial : materials) {
            if (existingMaterial.equals(newMaterial)) {
                return newMaterial;
            }
        }
        LOGGER.warn("Requested material " + newMaterial + " not found, substituting with " + materials[0] + ".");
        return materials[0];
    }

    private static Appearance getAppearanceFromMaterial(final String material) {
        final Color3f col = Preferences.getInstance().loadMaterialColor(material);
        final Appearance a = new Appearance();
        a.setMaterial(new Material(col, Constants.BLACK, col, Constants.BLACK, 101f));
        return a;
    }
}
