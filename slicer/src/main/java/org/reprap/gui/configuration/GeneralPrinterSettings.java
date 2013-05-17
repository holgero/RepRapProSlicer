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
import java.util.Collections;
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

import org.reprap.configuration.ExtruderSetting;
import org.reprap.configuration.PrinterSetting;

class GeneralPrinterSettings extends AbstractPrinterSettingPanel {
    private static final Icon ICON = new ImageIcon(GeneralPrinterSettings.class.getClassLoader().getResource("icons/cog.png"));
    private final JTextField bedSizeXField = new JTextField();
    private final JTextField bedSizeYField = new JTextField();
    private final JTextField maximumZField = new JTextField();
    private final JCheckBox relativeDistanceEField = new JCheckBox();
    private final JTextField maximumFeedrateXField = new JTextField();
    private final JTextField maximumFeedrateYField = new JTextField();
    private final JTextField maximumFeedrateZField = new JTextField();
    private final SpinnerNumberModel extrudersSpinnerModel = new SpinnerNumberModel(1, 1, 99, 1);

    GeneralPrinterSettings() {
        addComponents(createComponents(), true);
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
    void setValues(final PrinterSetting printerSetting) {
        bedSizeXField.setText(Double.toString(printerSetting.getBedSizeX()));
        bedSizeYField.setText(Double.toString(printerSetting.getBedSizeY()));
        maximumZField.setText(Double.toString(printerSetting.getMaximumZ()));
        relativeDistanceEField.setSelected(printerSetting.useRelativeDistanceE());
        maximumFeedrateXField.setText(Double.toString(printerSetting.getMaximumFeedrateX()));
        maximumFeedrateYField.setText(Double.toString(printerSetting.getMaximumFeedrateY()));
        maximumFeedrateZField.setText(Double.toString(printerSetting.getMaximumFeedrateZ()));
        extrudersSpinnerModel.setValue(printerSetting.getExtruderSettings().size());
    }

    @Override
    void getValues(final PrinterSetting printerSetting) {
        printerSetting.setBedSizeX(fieldToDouble(bedSizeXField));
        printerSetting.setBedSizeY(fieldToDouble(bedSizeYField));
        printerSetting.setMaximumZ(fieldToDouble(maximumZField));
        printerSetting.setRelativeDistanceE(relativeDistanceEField.isEnabled());
        printerSetting.setMaximumFeedrateX(fieldToDouble(maximumFeedrateXField));
        printerSetting.setMaximumFeedrateY(fieldToDouble(maximumFeedrateYField));
        printerSetting.setMaximumFeedrateZ(fieldToDouble(maximumFeedrateZField));
        final List<ExtruderSetting> previousExtruders = printerSetting.getExtruderSettings();
        final int newExtruderCount = ((Integer) extrudersSpinnerModel.getValue()).intValue();
        if (previousExtruders.size() != newExtruderCount) {
            final ExtruderSetting[] newExtruders = new ExtruderSetting[newExtruderCount];
            for (int i = 0; i < newExtruders.length; i++) {
                if (i < previousExtruders.size()) {
                    newExtruders[i] = previousExtruders.get(i);
                } else {
                    newExtruders[i] = new ExtruderSetting(previousExtruders.get(previousExtruders.size() - 1));
                }
            }
            printerSetting.setExtruderSettings(newExtruders);
        }
    }

    private List<? extends JComponent> createComponents() {
        final List<JComponent> result = new ArrayList<>();
        result.add(createSizePanel());
        result.add(createFirmwarePanel());
        result.add(createCapabilitiesPanel());
        return Collections.unmodifiableList(result);
    }

    private JPanel createSizePanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Sizes");
        panel.addRow(new JLabel("Bed size: "), new JLabel("x (mm): "), bedSizeXField, new JLabel("y (mm): "), bedSizeYField);
        panel.addRow(new JLabel("Maximum build height: "), new JLabel("z (mm): "), maximumZField);
        return panel;
    }

    private JPanel createFirmwarePanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Firmware");
        panel.addRow(new JLabel("Use relative E distances: "), relativeDistanceEField);
        return panel;
    }

    private JPanel createCapabilitiesPanel() {
        final SettingsBoxPanel panel = new SettingsBoxPanel("Capabilities");
        panel.addRow(new JLabel("Maximum Feedrate: "), new JLabel("x (mm/min): "), maximumFeedrateXField, new JLabel(
                "y (mm/min): "), maximumFeedrateYField, new JLabel("z (mm/min): "), maximumFeedrateZField);
        panel.addRow(new JLabel("Extruders: "), new JSpinner(extrudersSpinnerModel));
        return panel;
    }
}
