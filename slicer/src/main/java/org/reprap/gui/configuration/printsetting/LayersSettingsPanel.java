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
package org.reprap.gui.configuration.printsetting;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.reprap.configuration.PrintSetting;
import org.reprap.gui.configuration.common.SettingsBoxPanel;

public class LayersSettingsPanel extends AbstractPrintSettingPanel {
    private static final Icon ICON = createIcon("layers.png");
    private final JTextField layerHeight = new JTextField();
    private final SpinnerNumberModel verticalShells = new SpinnerNumberModel(1, 1, 99, 1);
    private final SpinnerNumberModel horizontalShells = new SpinnerNumberModel(1, 1, 99, 1);
    private final JCheckBox insideOut = new JCheckBox();
    private final JCheckBox middleStart = new JCheckBox();
    private final JTextField arcCompensation = new JTextField();
    private final JTextField arcShortSides = new JTextField();

    public LayersSettingsPanel() {
        addComponents(createComponents(), true);
    }

    private List<JComponent> createComponents() {
        final List<JComponent> result = new ArrayList<>();
        result.add(createLayerHeightPanel());
        result.add(createShellsPanel());
        result.add(createArcsPanel());
        return result;
    }

    private SettingsBoxPanel createLayerHeightPanel() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Layer height");
        result.addRow(new JLabel("Layer height: "), layerHeight, new JLabel(" mm"));
        return result;
    }

    private SettingsBoxPanel createShellsPanel() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Shells");
        result.addRow(new JLabel("Vertical shells (perimeters): "), new JSpinner(verticalShells));
        result.addRow(new JLabel("Print perimeters inside out"), insideOut);
        result.addRow(new JLabel("Middle start of perimeters"), middleStart);
        result.addRow(new JLabel("Horizontal shells (solid layers): "), new JSpinner(horizontalShells));
        return result;
    }

    private SettingsBoxPanel createArcsPanel() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Arc compensation");
        result.addRow(new JLabel("Arc compensation factor: "), arcCompensation);
        result.addRow(new JLabel("Arc short sides: "), arcShortSides, new JLabel(" mm"));
        return result;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Layers and Perimeters";
    }

    @Override
    void setValues(final PrintSetting printSetting) {
        layerHeight.setText(Double.toString(printSetting.getLayerHeight()));
        verticalShells.setValue(Integer.valueOf(printSetting.getVerticalShells()));
        horizontalShells.setValue(Integer.valueOf(printSetting.getHorizontalShells()));
        insideOut.setSelected(printSetting.isInsideOut());
        middleStart.setSelected(printSetting.isMiddleStart());
        arcCompensation.setText(Double.toString(printSetting.getArcCompensation()));
        arcShortSides.setText(Double.toString(printSetting.getArcShortSides()));
    }

    @Override
    void getValues(final PrintSetting printSetting) {
        printSetting.setLayerHeight(fieldToDouble(layerHeight));
        printSetting.setVerticalShells(((Integer) verticalShells.getValue()).intValue());
        printSetting.setHorizontalShells(((Integer) horizontalShells.getValue()).intValue());
        printSetting.setInsideOut(insideOut.isSelected());
        printSetting.setMiddleStart(middleStart.isSelected());
        printSetting.setArcCompensation(fieldToDouble(arcCompensation));
        printSetting.setArcShortSides(fieldToDouble(arcShortSides));
    }

}
