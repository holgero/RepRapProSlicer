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

import org.reprap.configuration.PrinterSetting;

public class GeneralPrinterSettings implements SettingsNode {
    private static final Icon ICON = new ImageIcon(GeneralPrinterSettings.class.getClassLoader().getResource(
            "icons/printer_empty.png"));
    private final PrinterSetting printerSetting;
    private JTextField bedSizeXField;
    private JTextField bedSizeYField;
    private JTextField maximumZField;
    private JCheckBox relativeDistanceEField;
    private JTextField maximumFeedrateXField;
    private JTextField maximumFeedrateYField;
    private JTextField maximumFeedrateZ;
    private SpinnerNumberModel extrudersSpinnerModel;

    public GeneralPrinterSettings(final PrinterSetting printerSetting) {
        this.printerSetting = printerSetting;
    }

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

    private JPanel createSizePanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Sizes");
        bedSizeXField = new JTextField(Double.toString(printerSetting.getBedSizeX()));
        bedSizeYField = new JTextField(Double.toString(printerSetting.getBedSizeY()));
        panel.addRow(new JLabel("Bed size: "), new JLabel("x (mm): "), bedSizeXField, new JLabel("y (mm): "), bedSizeYField);
        maximumZField = new JTextField("100");
        panel.addRow(new JLabel("Maximum build height: "), new JLabel("z (mm): "), maximumZField);
        return panel;
    }

    private JPanel createFirmwarePanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Firmware");
        relativeDistanceEField = new JCheckBox();
        relativeDistanceEField.setSelected(printerSetting.useRelativeDistanceE());
        panel.addRow(new JLabel("Use relative E distances: "), relativeDistanceEField);
        return panel;
    }

    private JPanel createCapabilitiesPanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Capabilities");
        maximumFeedrateXField = new JTextField(Double.toString(printerSetting.getMaximumFeedrateX()));
        maximumFeedrateYField = new JTextField(Double.toString(printerSetting.getMaximumFeedrateY()));
        maximumFeedrateZ = new JTextField(Double.toString(printerSetting.getMaximumFeedrateZ()));
        panel.addRow(new JLabel("Maximum Feedrate: "), new JLabel("x (mm/min): "), maximumFeedrateXField, new JLabel(
                "y (mm/min): "), maximumFeedrateYField, new JLabel("z (mm/min): "), maximumFeedrateZ);
        extrudersSpinnerModel = new SpinnerNumberModel(printerSetting.getExtruderSettings().size(), 1, 99, 1);
        panel.addRow(new JLabel("Extruders: "), new JSpinner(extrudersSpinnerModel));
        return panel;
    }

}
