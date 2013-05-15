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
package org.reprap.gui.configuration;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

public class GeneralPrinterSettings implements SettingsNode {
    private static final Icon ICON = new ImageIcon(GeneralPrinterSettings.class.getClassLoader().getResource(
            "icons/printer_empty.png"));

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "General";
    }

    @Override
    public List<? extends JComponent> getFormComponents() {
        final List<JComponent> result = new ArrayList<>();
        result.add(createSizePanel());
        result.add(createFirmwarePanel());
        result.add(createCapabilitiesPanel());
        return result;
    }

    private static JPanel createSizePanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Sizes");
        panel.addRow(new JLabel("Bed size: "), new JLabel("x (mm): "), new JTextField("200"), new JLabel("y (mm): "),
                new JTextField("200"));
        panel.addRow(new JLabel("Maximum build height: "), new JLabel("z (mm): "), new JTextField("100"));
        return panel;
    }

    private static JPanel createFirmwarePanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Firmware");
        panel.addRow(new JLabel("Use relative E distances: "), new JCheckBox());
        return panel;
    }

    private static JPanel createCapabilitiesPanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Capabilities");
        panel.addRow(new JLabel("Maximum Feedrate: "), new JLabel("x (mm/min): "), new JTextField("15000"), new JLabel(
                "y (mm/min): "), new JTextField("15000"), new JLabel("z (mm/min): "), new JTextField("200"));
        panel.addRow(new JLabel("Extruders: "), new JSpinner(new SpinnerNumberModel(1, 1, 99, 1)));
        return panel;
    }

}
