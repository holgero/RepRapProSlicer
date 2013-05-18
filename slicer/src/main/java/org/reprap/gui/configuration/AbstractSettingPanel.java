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
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

abstract class AbstractSettingPanel implements SettingsNode {

    private final JPanel panel = new JPanel();
    private final GridBagConstraints constraints = new GridBagConstraints();

    public AbstractSettingPanel() {
        panel.setLayout(new GridBagLayout());
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    GridBagConstraints getConstraints() {
        return constraints;
    }

    void addComponents(final List<? extends JComponent> components, final boolean pad) {
        for (final JComponent component : components) {
            panel.add(component, constraints);
            constraints.gridy++;
        }
        if (pad) {
            constraints.weighty = 1000.0;
            panel.add(new JLabel(), constraints);
        }
    }

    static final double fieldToDouble(final JTextField field) {
        return Double.parseDouble(field.getText());
    }

    static ImageIcon createIcon(final String name) {
        return new ImageIcon(AbstractSettingPanel.class.getClassLoader().getResource("icons/" + name));
    }
}
