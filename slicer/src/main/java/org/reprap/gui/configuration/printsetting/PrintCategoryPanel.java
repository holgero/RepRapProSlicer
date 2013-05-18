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
package org.reprap.gui.configuration.printsetting;

import javax.swing.Icon;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.PrintSetting;
import org.reprap.gui.configuration.common.AbstractCategoryPanel;

public class PrintCategoryPanel extends AbstractCategoryPanel {
    private static final Icon ICON = createIcon("wrench.png");

    public PrintCategoryPanel() {
        super("print setting", "Print Setting", createIcon("add.png"), createIcon("delete.png"));
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Print Settings";
    }

    @Override
    public void setValues(final Configuration configuration) {
        setValues(configuration.getCurrentConfiguration().getPrintSetting().getName(), configuration.getPrintSettings());
    }

    @Override
    public void getValues(final Configuration configuration) {
        performAdditions(configuration, PrintSetting.class);
        performDeletions(configuration.getPrintSettings());
        final String printSetting = getSelectedSetting();
        final PrintSetting setting = configuration.findSetting(printSetting, PrintSetting.class);
        if (setting == null) {
            throw new IllegalStateException("Unknown print setting >>" + printSetting + "<< in combo box.");
        }
        configuration.getCurrentConfiguration().setPrintSetting(setting);
    }
}
