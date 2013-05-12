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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.STLObject;

public class MaterialChooserDialog extends JDialog {
    private final JTextField copies;
    private final Attributes att;

    public MaterialChooserDialog(final Attributes a, final CurrentConfiguration currentConfiguration, final double volume) {
        super((JFrame) null, "Material selector");
        att = a;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        final JPanel radioPanel = new JPanel(new GridLayout(0, 1));
        radioPanel.setSize(300, 200);

        final JLabel jLabel0 = new JLabel();
        jLabel0.setText("Volume of object: " + Math.round(volume) + " mm^3");
        jLabel0.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(jLabel0);

        final JLabel jLabel2 = new JLabel();
        jLabel2.setText(" Number of copies of the object just loaded to print: ");
        jLabel2.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(jLabel2);

        copies = new JTextField("1");
        copies.setSize(20, 10);
        copies.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(copies);

        final JLabel jLabel1 = new JLabel();
        jLabel1.setText(" Select the material for the object(s): ");
        jLabel1.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(jLabel1);

        final List<MaterialSetting> materials = currentConfiguration.getMaterials();
        final String matname = att.getMaterial();

        final ButtonGroup bGroup = new ButtonGroup();
        for (final MaterialSetting material : materials) {
            final JRadioButton b = new JRadioButton(material.getName());
            b.setActionCommand(material.getName());
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    final String materialName = e.getActionCommand();
                    att.setMaterial(materialName);
                    att.setAppearance(STLObject.createAppearance(material));
                }
            });
            if (matname.contentEquals(material.getName())) {
                b.setSelected(true);
            }
            bGroup.add(b);
            radioPanel.add(b);
        }

        final JButton okButton = new JButton();
        radioPanel.add(okButton);
        okButton.setText("OK");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent evt) {
                //                final int number = Integer.parseInt(copies.getText().trim()) - 1;
                //                final STLObject stl = rrb.getSTLs().get(stlIndex);
                //                rrb.moreCopies(stl, att, number);
                //                dialog.dispose();
            }
        });

        add(radioPanel, BorderLayout.LINE_START);
    }
}
