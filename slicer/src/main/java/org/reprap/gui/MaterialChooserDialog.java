/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *  contains code from MaterialRadioButtons, (C) by Adrian Boyer
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

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.STLObject;

public class MaterialChooserDialog extends JDialog {
    private final JSpinner copies = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final Attributes attributes;
    private final CurrentConfiguration currentConfiguration;
    private final STLObject stl;

    public MaterialChooserDialog(final Attributes attributes, final CurrentConfiguration currentConfiguration,
            final RepRapPlater plater, final STLObject stl) {
        super((JFrame) null, "Material selector");
        this.attributes = attributes;
        this.currentConfiguration = currentConfiguration;
        this.stl = stl;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        add(createFormularPanel(), BorderLayout.CENTER);
        add(createOkButton(plater), BorderLayout.SOUTH);
    }

    private JButton createOkButton(final RepRapPlater plater) {
        final JButton okButton = new JButton();
        okButton.setText("OK");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent evt) {
                final int count = ((Integer) copies.getValue()).intValue() - 1;
                plater.moreCopies(stl, attributes, count);
                dispose();
            }
        });
        return okButton;
    }

    private JPanel createFormularPanel() {
        final JPanel formularPanel = new JPanel(new GridBagLayout());
        formularPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.gridx = 0;
        constraints.gridy = 0;

        formularPanel.add(new JLabel("Volume of object: "), constraints);
        constraints.gridx++;

        formularPanel.add(new JLabel(Math.round(stl.volume()) + " mm^3"), constraints);
        constraints.gridx = 0;
        constraints.gridy++;

        formularPanel.add(new JLabel("Copies: "), constraints);
        constraints.gridx++;

        formularPanel.add(copies, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        final Box buttonBox = createMaterialsButtonBox();
        formularPanel.add(buttonBox, constraints);
        constraints.gridy++;
        constraints.gridwidth = 1;
        return formularPanel;
    }

    private Box createMaterialsButtonBox() {
        final Box buttonBox = new Box(BoxLayout.Y_AXIS);
        buttonBox.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Material"));
        final ButtonGroup group = new ButtonGroup();
        for (final MaterialSetting material : currentConfiguration.getMaterials()) {
            final JRadioButton button = new JRadioButton(material.getName());
            button.setActionCommand(material.getName());
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    final String materialName = e.getActionCommand();
                    attributes.setMaterial(materialName);
                    attributes.setAppearance(STLObject.createAppearance(material));
                }
            });
            if (attributes.getMaterial().equals(material.getName())) {
                button.setSelected(true);
            }
            group.add(button);
            buttonBox.add(button);
        }
        return buttonBox;
    }
}
