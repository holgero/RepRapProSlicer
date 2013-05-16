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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;

import org.reprap.configuration.Configuration;

public class ButtonsPanel extends JComponent {

    private final SettingsEditor settingsEditor;
    private final Configuration configuration;
    private final SettingsNode settings;

    public ButtonsPanel(final SettingsEditor settingsEditor, final Configuration configuration, final SettingsNode settings) {
        this.settingsEditor = settingsEditor;
        this.configuration = configuration;
        this.settings = settings;
        setBorder(new EmptyBorder(3, 3, 3, 3));
        setLayout(new GridBagLayout());
        addButtons();
    }

    private void addButtons() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.ipadx = 2;
        constraints.ipady = 2;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        add(createRestoreButton(), constraints);
        constraints.gridx++;
        constraints.weightx = 5;
        add(createApplyButton(), constraints);
        constraints.gridx++;
        constraints.weightx = 1;
        add(createSaveButton(), constraints);
        constraints.gridx++;
    }

    private JButton createRestoreButton() {
        final JButton button = new JButton("Restore");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                settings.setValues(configuration);
            }
        });
        return button;
    }

    private JButton createApplyButton() {
        final JButton button = new JButton("Apply");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                settings.getValues(configuration);
                settingsEditor.updateTree();
            }
        });
        return button;
    }

    private JButton createSaveButton() {
        final JButton button = new JButton("Save");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                settings.getValues(configuration);
                settingsEditor.updateTree();
                configuration.save();
            }
        });
        return button;
    }
}
