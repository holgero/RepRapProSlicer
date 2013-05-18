/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.reprap.gui.configuration.material;

import javax.swing.Icon;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.gui.configuration.common.AbstractCategoryPanel;

public class MaterialCategoryPanel extends AbstractCategoryPanel {
    private static final Icon ICON = createIcon("color_swatch.png");

    public MaterialCategoryPanel() {
        super("material", "Material", createIcon("add.png"), createIcon("delete.png"));
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Material Settings";
    }

    @Override
    public void setValues(final Configuration configuration) {
        setValues(configuration.getCurrentConfiguration().getMaterials().get(0).getName(), configuration.getMaterials());
    }

    @Override
    public void getValues(final Configuration configuration) {
        performAdditions(configuration, MaterialSetting.class);
        performDeletions(configuration.getMaterials());
        final String material = getSelectedSetting();
        final MaterialSetting setting = configuration.findSetting(material, MaterialSetting.class);
        if (setting == null) {
            throw new IllegalStateException("Unknown material >>" + material + "<< in combo box.");
        }
    }

}
