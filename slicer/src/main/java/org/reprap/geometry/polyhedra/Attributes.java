package org.reprap.geometry.polyhedra;

import java.util.List;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Configuration;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;

/**
 * Holds RepRap attributes that are attached to Java3D shapes as user data,
 * primarily to record the material that things are made from.
 * 
 * @author adrian
 */
public class Attributes {
    private static final Logger LOGGER = LogManager.getLogger(Attributes.class);
    private MaterialSetting material;
    private final STLObject parent;
    private Appearance appearance;

    public Attributes(final STLObject parent, final Appearance appearance) {
        material = null;
        this.parent = parent;
        this.appearance = appearance;
    }

    @Override
    public String toString() {
        return "Attributes: material is " + material;
    }

    public String getMaterial() {
        if (material == null) {
            return null;
        }
        return material.getName();
    }

    public STLObject getParent() {
        return parent;
    }

    public Appearance getAppearance() {
        return appearance;
    }

    public void setMaterial(final String newMaterial) {
        material = getMaterialSettings(newMaterial);
        appearance = createAppearance(material);
        if (parent != null) {
            parent.restoreAppearance();
        }
    }

    private static Appearance createAppearance(final MaterialSetting material) {
        final Color3f color = material.getColor();
        final Appearance appearance = new Appearance();
        appearance.setMaterial(new Material(color, Constants.BLACK, color, Constants.BLACK, 101f));
        return appearance;
    }

    private static MaterialSetting getMaterialSettings(final String materialName) {
        final CurrentConfiguration configuration = Configuration.getInstance().getCurrentConfiguration();
        final List<MaterialSetting> materials = configuration.getMaterials();
        for (final MaterialSetting material : materials) {
            if (material.getName().equals(materialName)) {
                return material;
            }
        }
        final MaterialSetting substitute = materials.get(0);
        LOGGER.warn("Requested material " + materialName + " not found, substituting with " + substitute.getName() + ".");
        return substitute;
    }
}
