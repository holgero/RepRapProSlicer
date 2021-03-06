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

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;

import org.reprap.configuration.Configuration;
import org.reprap.gui.configuration.common.SettingsNode;
import org.reprap.gui.configuration.printer.ExtruderPanel;

public class SettingsEditor extends JPanel implements TreeSelectionListener {
    private final TopicSelectionTree tree = new TopicSelectionTree();
    private final JPanel rightPanel = new JPanel();
    private final Configuration configuration;
    private JPanel formPanel;
    private JComponent buttonsPanel;

    public SettingsEditor(final Configuration configuration) {
        this.configuration = configuration;
        setLayout(new BorderLayout());
        add(createTopicsTreePanel(tree), BorderLayout.WEST);
        rightPanel.setLayout(new BorderLayout());
        rightPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        add(rightPanel, BorderLayout.CENTER);
    }

    public void hookListener(final boolean enable) {
        if (enable) {
            tree.getSelectionModel().addTreeSelectionListener(this);
            updateTree();
            updateRightPanel();
        } else {
            tree.getSelectionModel().removeTreeSelectionListener(this);
        }
    }

    void updateTree() {
        final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        final Object root = model.getRoot();
        final DefaultMutableTreeNode printerSettings = (DefaultMutableTreeNode) model.getChild(root, 0);
        int treeExtruders = printerSettings.getChildCount() - 2;
        final int currentExtruders = configuration.getCurrentConfiguration().getPrinterSetting().getExtruderSettings().size();
        while (treeExtruders < currentExtruders) {
            treeExtruders++;
            model.insertNodeInto(new DefaultMutableTreeNode(new ExtruderPanel(treeExtruders)), printerSettings,
                    treeExtruders + 2 - 1);
        }
        while (treeExtruders > currentExtruders) {
            model.removeNodeFromParent((MutableTreeNode) printerSettings.getChildAt(treeExtruders + 2 - 1));
            treeExtruders--;
        }
    }

    private static JPanel createTopicsTreePanel(final JTree tree) {
        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(new BevelBorder(BevelBorder.LOWERED));
        panel.add(tree, BorderLayout.WEST);
        panel.setBackground(Color.WHITE);
        return panel;
    }

    @Override
    public void valueChanged(final TreeSelectionEvent event) {
        updateRightPanel();
    }

    private void updateRightPanel() {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) {
            return;
        }
        final Object userData = node.getUserObject();
        if (userData instanceof SettingsNode) {
            final SettingsNode settings = (SettingsNode) userData;
            if (formPanel != null) {
                rightPanel.remove(formPanel);
            }
            formPanel = settings.getPanel();
            rightPanel.add(formPanel, BorderLayout.CENTER);
            settings.setValues(configuration);
            if (buttonsPanel != null) {
                rightPanel.remove(buttonsPanel);
            }
            buttonsPanel = new ButtonsPanel(this, configuration, settings);
            rightPanel.add(buttonsPanel, BorderLayout.SOUTH);
            getParent().validate();
            repaint();
        }
    }
}
