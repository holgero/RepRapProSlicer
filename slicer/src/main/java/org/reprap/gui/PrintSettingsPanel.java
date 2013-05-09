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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;

public class PrintSettingsPanel extends JPanel {

    PrintSettingsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(createPrinterSettingTopicsTree());
        add(createPrinterSettingFormPane());
    }

    private Component createPrinterSettingFormPane() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(createLayerHeightPanel());
        panel.setPreferredSize(new Dimension(500, 500));
        return panel;
    }

    private JPanel createLayerHeightPanel() {
        final JPanel panel = new JPanel();
        panel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        panel.add(new JLabel("Layer height: "));
        panel.setPreferredSize(new Dimension(500, 50));
        return panel;
    }

    private JPanel createPrinterSettingTopicsTree() {
        final JPanel panel = new JPanel();
        panel.setBorder(new BevelBorder(BevelBorder.LOWERED));
        panel.add(new JTree(new String[] { "Layers", "Infill" }));
        panel.setPreferredSize(new Dimension(100, 500));
        panel.setBackground(Color.WHITE);
        return panel;
    }
}
