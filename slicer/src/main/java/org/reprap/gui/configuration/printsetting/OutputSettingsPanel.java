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

import java.util.Collections;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.reprap.configuration.PrintSetting;
import org.reprap.gui.configuration.common.SettingsBoxPanel;

public class OutputSettingsPanel extends AbstractPrintSettingPanel {
    private static final Icon ICON = createIcon("page_white_go.png");
    private final JCheckBox verboseGCode = new JCheckBox();
    private final JCheckBox pathOptimize = new JCheckBox();

    public OutputSettingsPanel() {
        final SettingsBoxPanel box = new SettingsBoxPanel("Output");
        box.addRow(new JLabel("Verbose G-code: "), verboseGCode);
        box.addRow(new JLabel("Optimize paths: "), pathOptimize);
        addComponents(Collections.singletonList(box), true);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Output options";
    }

    @Override
    void setValues(final PrintSetting printSetting) {
        verboseGCode.setSelected(printSetting.isVerboseGCode());
        pathOptimize.setSelected(printSetting.isPathOptimize());
    }

    @Override
    void getValues(final PrintSetting printSetting) {
        printSetting.setVerboseGCode(verboseGCode.isSelected());
        printSetting.setPathOptimize(pathOptimize.isSelected());
    }
}
