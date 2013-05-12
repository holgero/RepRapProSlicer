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
import java.util.Map;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;

import org.reprap.configuration.Configuration;

public class PlaterPanel extends JPanel {

    private static final Insets BIG_INSETS = new Insets(5, 5, 5, 5);

    public PlaterPanel(final Configuration configuration, final RepRapPlater plater, final Map<String, Action> actions) {
        setLayout(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridheight = GridBagConstraints.REMAINDER;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        add(makePlaterBox(plater), constraints);
        constraints.gridheight = 1;
        constraints.gridy = 0;
        constraints.gridx++;
        constraints.weightx = 0;
        add(new CurrentConfigurationStateBox(configuration), constraints);
        constraints.gridy++;
        add(makeButtonsBox(actions), constraints);
    }

    private static JPanel makePlaterBox(final RepRapPlater plater) {
        plater.setBorder(new EtchedBorder());
        return plater;
    }

    private static JPanel makeButtonsBox(final Map<String, Action> actions) {
        final JPanel buttonsBox = new JPanel();
        buttonsBox.setLayout(new GridBagLayout());
        buttonsBox.setBorder(new EtchedBorder());
        addButtons(buttonsBox, actions);
        return buttonsBox;
    }

    private static void addButtons(final JPanel buttonsBox, final Map<String, Action> actions) {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.ipadx = 5;
        constraints.ipady = 5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = BIG_INSETS;
        final JButton loadRFO = new JButton(actions.get(MainFrame.LOAD_RFO_ACTION));
        loadRFO.setBackground(new java.awt.Color(0, 204, 255));
        buttonsBox.add(loadRFO, constraints);
        constraints.gridy++;

        final JButton saveRFO = new JButton(actions.get(MainFrame.SAVE_RFO_ACTION));
        saveRFO.setBackground(new java.awt.Color(0, 204, 255));
        buttonsBox.add(saveRFO, constraints);
        constraints.gridy++;

        final JButton loadSTL = new JButton(actions.get(MainFrame.LOAD_STL_CSG_ACTION));
        loadSTL.setBackground(new java.awt.Color(0, 204, 255));
        buttonsBox.add(loadSTL, constraints);
        constraints.gridy++;

        final JButton sliceButton = new JButton(actions.get(MainFrame.SLICE_ACTION));
        sliceButton.setBackground(new java.awt.Color(51, 204, 0));
        buttonsBox.add(sliceButton, constraints);
        constraints.gridy++;

        final JButton exitButton = new JButton(actions.get(MainFrame.EXIT_ACTION));
        buttonsBox.add(exitButton, constraints);
        constraints.gridy++;
    }
}
