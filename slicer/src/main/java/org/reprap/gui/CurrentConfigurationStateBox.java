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
package org.reprap.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.configuration.NamedSetting;
import org.reprap.configuration.PrintSetting;
import org.reprap.configuration.PrinterSetting;

class CurrentConfigurationStateBox extends JPanel implements ItemListener {
    private static final Insets SMALL_INSETS = new Insets(2, 2, 2, 2);
    private final Configuration configuration;
    private final JComboBox<String> printSettingsCombo;
    private final JComboBox<String> printerSettingsCombo;
    private final List<JComboBox<String>> materialComboBoxes = new ArrayList<>();

    CurrentConfigurationStateBox(final Configuration configuration) {
        this.configuration = configuration;
        printSettingsCombo = new JComboBox<>(getNames(configuration.getPrintSettings()));
        printerSettingsCombo = new JComboBox<>(getNames(configuration.getPrinterSettings()));
        setLayout(new GridBagLayout());
        setBorder(new TitledBorder(new EtchedBorder(), "Current Settings"));
        addComponents();
        updateCombos();
        registerMe();
    }

    private void registerMe() {
        printSettingsCombo.addItemListener(this);
        printerSettingsCombo.addItemListener(this);
        for (final JComboBox<String> combo : materialComboBoxes) {
            combo.addItemListener(this);
        }
    }

    private void updateCombos() {
        final CurrentConfiguration currentConfiguration = configuration.getCurrentConfiguration();
        printSettingsCombo.setSelectedItem(currentConfiguration.getPrintSetting().getName());
        printerSettingsCombo.setSelectedItem(currentConfiguration.getPrinterSetting().getName());
        updateMaterialCombos(currentConfiguration);
    }

    private void updateMaterialCombos(final CurrentConfiguration currentConfiguration) {
        for (int i = 0; i < currentConfiguration.getMaterials().size(); i++) {
            final MaterialSetting material = currentConfiguration.getMaterials().get(i);
            materialComboBoxes.get(i).setSelectedItem(material.getName());
            materialComboBoxes.get(i).setEnabled(true);
        }
        for (int i = currentConfiguration.getPrinterSetting().getExtruderSettings().size(); i < materialComboBoxes.size(); i++) {
            materialComboBoxes.get(i).setEnabled(false);
        }
    }

    private void addComponents() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.ipadx = 5;
        constraints.ipady = 5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = SMALL_INSETS;
        add(new JLabel("Print Settings"), constraints);
        constraints.gridy++;
        add(printSettingsCombo, constraints);
        constraints.gridy++;
        add(new JLabel("Material"), constraints);
        constraints.gridy++;
        final String[] materialNames = getNames(configuration.getMaterials());
        final int maxCombos = countMaximumMaterials();
        for (int i = 0; i < maxCombos; i++) {
            final JComboBox<String> materialCombo = new JComboBox<>(materialNames);
            materialComboBoxes.add(materialCombo);
            add(materialCombo, constraints);
            constraints.gridy++;
        }
        add(new JLabel("Printer"), constraints);
        constraints.gridy++;
        add(printerSettingsCombo, constraints);
        constraints.gridy++;
    }

    private int countMaximumMaterials() {
        int result = 1;
        for (final PrinterSetting printerSetting : configuration.getPrinterSettings()) {
            final int extruders = printerSetting.getExtruderSettings().size();
            if (extruders > result) {
                result = extruders;
            }
        }
        return result;
    }

    private static String[] getNames(final List<? extends NamedSetting> namedSetting) {
        final List<String> settingsNames = new ArrayList<>();
        for (final NamedSetting setting : namedSetting) {
            settingsNames.add(setting.getName());
        }
        return settingsNames.toArray(new String[settingsNames.size()]);
    }

    @Override
    public void itemStateChanged(final ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            final JComboBox<?> source = (JComboBox<?>) e.getSource();
            final CurrentConfiguration currentConfiguration = configuration.getCurrentConfiguration();
            if (source == printerSettingsCombo) {
                for (final PrinterSetting printerSetting : configuration.getPrinterSettings()) {
                    if (printerSetting.getName().equals(e.getItem())) {
                        currentConfiguration.setPrinterSetting(printerSetting);
                    }
                }
                updateMaterialCombos(currentConfiguration);
            } else if (source == printSettingsCombo) {
                for (final PrintSetting printSetting : configuration.getPrintSettings()) {
                    if (printSetting.getName().equals(e.getItem())) {
                        currentConfiguration.setPrintSetting(printSetting);
                    }
                }
            } else {
                for (int i = 0; i < materialComboBoxes.size(); i++) {
                    final JComboBox<?> materialCombo = materialComboBoxes.get(i);
                    if (source == materialCombo) {
                        for (final MaterialSetting materialSetting : configuration.getMaterials()) {
                            if (materialSetting.getName().equals(e.getItem())) {
                                currentConfiguration.setMaterial(i, materialSetting);
                            }
                        }
                    }
                }
            }
            configuration.save();
        }
    }
}
