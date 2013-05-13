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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

public class SettingsPanel extends JPanel implements TreeSelectionListener {
    private final TopicSelectionTree tree = new TopicSelectionTree();
    private final JPanel formPanel = createSettingFormPane();

    public SettingsPanel() {
        setLayout(new BorderLayout());
        add(createSettingTopicsTree(tree), BorderLayout.WEST);
        add(formPanel, BorderLayout.CENTER);
    }

    public void hookListener(final boolean enable) {
        if (enable) {
            tree.getSelectionModel().addTreeSelectionListener(this);
        } else {
            tree.getSelectionModel().removeTreeSelectionListener(this);
        }
    }

    private static JPanel createSettingFormPane() {
        final JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        return panel;
    }

    private static JPanel createSettingTopicsTree(final JTree tree) {
        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(new BevelBorder(BevelBorder.LOWERED));
        panel.add(tree, BorderLayout.WEST);
        panel.setBackground(Color.WHITE);
        return panel;
    }

    @Override
    public void valueChanged(final TreeSelectionEvent event) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        final Object userData = node.getUserObject();
        if (userData instanceof SettingsNode) {
            final SettingsNode settings = (SettingsNode) userData;
            formPanel.removeAll();
            final GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(2, 2, 2, 2);
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTH;
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.weightx = 1.0;
            constraints.weighty = 1.0;
            for (final JComponent component : settings.getFormComponents()) {
                formPanel.add(component, constraints);
                constraints.gridy++;
            }
            getParent().validate();
            repaint();
        }
    }
}
