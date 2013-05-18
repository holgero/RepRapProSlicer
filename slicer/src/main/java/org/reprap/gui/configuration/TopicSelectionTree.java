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

import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;

import org.reprap.gui.configuration.common.SettingsNode;
import org.reprap.gui.configuration.material.FilamentSettingsPanel;
import org.reprap.gui.configuration.material.MaterialCategoryPanel;
import org.reprap.gui.configuration.printer.CustomGcodePanel;
import org.reprap.gui.configuration.printer.ExtruderPanel;
import org.reprap.gui.configuration.printer.GeneralPrinterSettings;
import org.reprap.gui.configuration.printer.PrinterCategoryPanel;
import org.reprap.gui.configuration.printsetting.InfillSettingsPanel;
import org.reprap.gui.configuration.printsetting.LayersSettingsPanel;
import org.reprap.gui.configuration.printsetting.OutputSettingsPanel;
import org.reprap.gui.configuration.printsetting.PrintCategoryPanel;
import org.reprap.gui.configuration.printsetting.SkirtBrimSettingsPanel;
import org.reprap.gui.configuration.printsetting.SpeedSettingsPanel;
import org.reprap.gui.configuration.printsetting.SupportSettingPanel;

public class TopicSelectionTree extends JTree {

    public TopicSelectionTree() {
        setModel(createConfigurationTreeModel());
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setCellRenderer(new SettingsTreeCellRenderer());
        setRootVisible(false);
        expandRow(2);
        expandRow(1);
        expandRow(0);
        setSelectionRow(0);
        setBorder(new EmptyBorder(5, 10, 5, 10));
    }

    private static TreeModel createConfigurationTreeModel() {
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        final DefaultTreeModel result = new DefaultTreeModel(root);
        root.add(createNode(new PrinterCategoryPanel(), new GeneralPrinterSettings(), new CustomGcodePanel(),
                new ExtruderPanel(1)));
        root.add(createNode(new PrintCategoryPanel(), new LayersSettingsPanel(), new InfillSettingsPanel(),
                new SpeedSettingsPanel(), new SkirtBrimSettingsPanel(), new SupportSettingPanel(), new OutputSettingsPanel()));
        root.add(createNode(new MaterialCategoryPanel(), new FilamentSettingsPanel()));

        return result;
    }

    private static MutableTreeNode createNode(final SettingsNode category, final SettingsNode... settings) {
        final DefaultMutableTreeNode node = new DefaultMutableTreeNode(category);
        for (final SettingsNode setting : settings) {
            node.add(new DefaultMutableTreeNode(setting));
        }
        return node;
    }
}
