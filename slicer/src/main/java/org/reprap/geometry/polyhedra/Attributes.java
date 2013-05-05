package org.reprap.geometry.polyhedra;

import java.util.List;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Configuration;
import org.reprap.configuration.ExtruderSetting;
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

    public Attributes(final MaterialSetting material) {
        this(material, null, createAppearance(material));
    }

    public Attributes(final STLObject parent, final Appearance appearance) {
        this(null, parent, appearance);
    }

    private Attributes(final MaterialSetting material, final STLObject parent, final Appearance appearance) {
        this.material = material;
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

    private static MaterialSetting getMaterialSettings(final String material) {
        final List<ExtruderSetting> extruderSettings = Configuration.getInstance().getCurrentConfiguration().getPrinterSetting()
                .getExtruderSettings();
        for (final ExtruderSetting setting : extruderSettings) {
            final MaterialSetting materialSettings = setting.getMaterial();
            if (materialSettings.getName().equals(material)) {
                return materialSettings;
            }
        }
        final MaterialSetting substitute = extruderSettings.get(0).getMaterial();
        LOGGER.warn("Requested material " + material + " not found, substituting with " + substitute.getName() + ".");
        return substitute;
    }
}
