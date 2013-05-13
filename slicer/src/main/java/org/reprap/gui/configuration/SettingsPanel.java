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
import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;

public class SettingsPanel extends JPanel {

    public SettingsPanel() {
        setLayout(new BorderLayout());
        add(createSettingTopicsTree(), BorderLayout.WEST);
        add(createSettingFormPane(), BorderLayout.CENTER);
    }

    private Component createSettingFormPane() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        return panel;
    }

    private JPanel createSettingTopicsTree() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(new BevelBorder(BevelBorder.LOWERED));
        final TreeModel model = createConfigurationTreeModel();
        final JTree tree = new JTree(model);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new SettingsTreeCellRenderer());
        tree.setRootVisible(false);
        tree.expandRow(2);
        tree.expandRow(1);
        tree.expandRow(0);
        tree.setBorder(new EmptyBorder(5, 10, 5, 10));
        panel.add(tree, BorderLayout.WEST);
        panel.setBackground(Color.WHITE);
        return panel;
    }

    private TreeModel createConfigurationTreeModel() {
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        final DefaultTreeModel result = new DefaultTreeModel(root);
        root.add(createNode("Printer Settings", "General", "Custom G-Code"));
        root.add(createNode("Print Settings", "Layers and Perimeters", "Infill", "Speed", "Skirt and Brim", "Support material",
                "Output options", "Multiple Extruders", "Advanced"));
        root.add(createNode("Material Settings", "Filament", "Cooling"));

        return result;
    }

    private MutableTreeNode createNode(final String category, final String... settings) {
        final DefaultMutableTreeNode printSettingsNode = new DefaultMutableTreeNode(category);
        for (final String setting : settings) {
            printSettingsNode.add(new DefaultMutableTreeNode(new DummySettingsPanel(setting)));
        }
        return printSettingsNode;
    }
}
