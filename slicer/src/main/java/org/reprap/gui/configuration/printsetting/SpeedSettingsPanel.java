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
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.reprap.configuration.PrintSetting;
import org.reprap.gui.configuration.common.SettingsBoxPanel;

public class SpeedSettingsPanel extends AbstractPrintSettingPanel {
    private static final Icon ICON = createIcon("time.png");

    private final JTextField perimeterSpeed = new JTextField();
    private final JTextField infillSpeed = new JTextField();

    public SpeedSettingsPanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Speed");
        panel.addRow(new JLabel("Perimeter speed ratio (0..1): "), perimeterSpeed);
        panel.addRow(new JLabel("Infill speed ratio (0..1): "), infillSpeed);
        addComponents(Collections.singletonList(panel), true);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Speed";
    }

    @Override
    void setValues(final PrintSetting printSetting) {
        perimeterSpeed.setText(Double.toString(printSetting.getPerimeterSpeed()));
        infillSpeed.setText(Double.toString(printSetting.getInfillSpeed()));
    }

    @Override
    void getValues(final PrintSetting printSetting) {
        printSetting.setPerimeterSpeed(fieldToDouble(perimeterSpeed));
        printSetting.setInfillSpeed(fieldToDouble(infillSpeed));
    }
}
