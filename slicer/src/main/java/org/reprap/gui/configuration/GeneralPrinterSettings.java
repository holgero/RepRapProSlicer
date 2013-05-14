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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

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
        return result;
    }

    private static JPanel createSizePanel() {
        final JPanel sizePanel = new JPanel();
        sizePanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Sizes"));
        sizePanel.setLayout(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        sizePanel.add(new JLabel("Bed size: "), constraints);
        constraints.gridx++;
        constraints.weightx = 0;
        sizePanel.add(new JLabel("x (mm): "), constraints);
        constraints.gridx++;
        constraints.weightx = 1.0;
        sizePanel.add(new JTextField("200"), constraints);
        constraints.gridx++;
        constraints.weightx = 0;
        sizePanel.add(new JLabel("y (mm): "), constraints);
        constraints.gridx++;
        constraints.weightx = 1.0;
        sizePanel.add(new JTextField("200"), constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        sizePanel.add(new JLabel("Maximum build height: "), constraints);
        constraints.gridx++;
        constraints.weightx = 0;
        sizePanel.add(new JLabel("z (mm): "), constraints);
        constraints.gridx++;
        constraints.weightx = 1.0;
        sizePanel.add(new JTextField("100"), constraints);
        return sizePanel;
    }

    private static JPanel createFirmwarePanel() {
        final JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Firmware"));
        panel.setLayout(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        panel.add(new JLabel("Use relative E distances: "), constraints);
        constraints.gridx++;
        panel.add(new JCheckBox(), constraints);
        return panel;
    }

}
