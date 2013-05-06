package org.reprap.geometry.polyhedra;

import javax.media.j3d.Appearance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Holds RepRap attributes that are attached to Java3D shapes as user data,
 * primarily to record the material that things are made from.
 * 
 * @author adrian
 */
public class Attributes {
    static final Logger LOGGER = LogManager.getLogger(Attributes.class);
    private String material;
    private final STLObject parent;
    private Appearance appearance;

    public Attributes(final STLObject parent, final Appearance appearance, final String material) {
        this.material = material;
        this.parent = parent;
        this.appearance = appearance;
    }

    @Override
    public String toString() {
        return "Attributes: material is " + material;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(final String material) {
        this.material = material;
    }

    public STLObject getParent() {
        return parent;
    }

    public Appearance getAppearance() {
        return appearance;
    }

    public void setAppearance(final Appearance appearance) {
        this.appearance = appearance;
        if (parent != null) {
            parent.restoreAppearance();
        }
    }
}
