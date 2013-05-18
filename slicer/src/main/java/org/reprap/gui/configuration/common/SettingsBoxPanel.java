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
package org.reprap.gui.configuration.common;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

public class SettingsBoxPanel extends JPanel {
    private final GridBagConstraints constraints;

    public SettingsBoxPanel(final String title) {
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), title));
        setLayout(new GridBagLayout());
        constraints = createConstraints();
    }

    private static GridBagConstraints createConstraints() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.ipadx = 2;
        constraints.ipady = 2;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        return constraints;
    }

    public void addRow(final JComponent... components) {
        for (final JComponent component : components) {
            super.add(component, constraints);
            constraints.gridx++;
        }
        addFiller();
        constraints.gridx = 0;
        constraints.gridy++;
    }

    private void addFiller() {
        constraints.weightx = 1;
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        super.add(new JLabel(), constraints);
        constraints.weightx = 0;
        constraints.gridwidth = 1;
    }
}
