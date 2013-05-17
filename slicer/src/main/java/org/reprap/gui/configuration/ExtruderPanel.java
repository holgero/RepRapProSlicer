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

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.reprap.configuration.PrinterSetting;

class ExtruderPanel extends AbstractPrinterSettingPanel {
    private static final Icon ICON = new ImageIcon(ExtruderPanel.class.getClassLoader().getResource("icons/funnel.png"));

    private final JTextField nozzleDiameter = new JTextField();
    private final JTextField extrudeRatio = new JTextField();
    private final JTextField airExtrusionFeedRate = new JTextField();
    private final JTextField printExtrusionFeedRate = new JTextField();
    private final JTextField retraction = new JTextField();
    private final JTextField extraLengthPerLayer = new JTextField();
    private final JTextField extraLengthPerPolygon = new JTextField();
    private final JTextField extrusionOverrun = new JTextField();
    private final JTextField lift = new JTextField();
    private final int number;

    ExtruderPanel(final int number) {
        this.number = number;
        addComponents(getFormComponents(), true);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Extruder " + number;
    }

    private List<? extends JComponent> getFormComponents() {
        final List<JComponent> result = new ArrayList<>();
        result.add(createSizePanel());
        result.add(createExtrusionSpeedsPanel());
        result.add(createRetractionPanel());
        result.add(createLiftPanel());
        return result;
    }

    private SettingsBoxPanel createSizePanel() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Size");
        result.addRow(new JLabel("Nozzle diameter: "), nozzleDiameter, new JLabel(" mm"));
        return result;
    }

    private SettingsBoxPanel createExtrusionSpeedsPanel() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Extrusion Speeds");
        result.addRow(new JLabel("Extrude ratio: "), extrudeRatio, new JLabel(" ratio"));
        result.addRow(new JLabel("Feedrate in air: "), airExtrusionFeedRate, new JLabel(" mm/min"));
        result.addRow(new JLabel("Feedrate printing: "), printExtrusionFeedRate, new JLabel(" mm/min"));
        return result;
    }

    private SettingsBoxPanel createRetractionPanel() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Retraction");
        result.addRow(new JLabel("Retraction length: "), retraction, new JLabel(" mm"));
        result.addRow(new JLabel("Extra length on layer start: "), extraLengthPerLayer, new JLabel(" mm"));
        result.addRow(new JLabel("Extra length on polygon start: "), extraLengthPerPolygon, new JLabel(" mm"));
        result.addRow(new JLabel("Extrusion overrun: "), extrusionOverrun, new JLabel(" mm"));
        return result;
    }

    private SettingsBoxPanel createLiftPanel() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Lift");
        result.addRow(new JLabel("Lift: "), lift, new JLabel(" mm"));
        return result;
    }

    @Override
    void setValues(final PrinterSetting printerSetting) {
        // TODO Auto-generated method stub
    }

    @Override
    void getValues(final PrinterSetting printerSetting) {
        // TODO Auto-generated method stub
    }
}
