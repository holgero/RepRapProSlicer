package org.reprap.geometry.polyhedra;

import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;
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
    public static final Logger LOGGER = LogManager.getLogger(Attributes.class);
    /**
     * The name of the material
     */
    private String material;

    /**
     * The STLObject of which this is a part
     */
    private final STLObject parent;

    /**
     * Where this is in the STLObject of which it is a part
     */
    private BranchGroup part;

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
     * @return the bit of the STLObject that this is
     */
    public BranchGroup getPart() {
        return part;
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
    public void setMaterial(final String s) {
        material = s;
        app = getAppearanceFromMaterial(material);
        if (parent != null) {
            parent.restoreAppearance();
        }
    }

    private static Appearance getAppearanceFromMaterial(final String material) {
        final Appearance a = new Appearance();
        final Color3f col = Preferences.getInstance().loadMaterialColor(material);
        a.setMaterial(new Material(col, Constants.BLACK, col, Constants.BLACK, 101f));
        return a;
    }

    /**
     * To be used in conjunction with changing the parent
     */
    public void setPart(final BranchGroup b) {
        part = b;
    }
}
