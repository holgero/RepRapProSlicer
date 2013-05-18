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

public class SkirtBrimSettingsPanel extends AbstractPrintSettingPanel {
    private static final Icon ICON = createIcon("box.png");

    private final JCheckBox skirt = new JCheckBox();
    private final SpinnerNumberModel brimLines = new SpinnerNumberModel(0, 0, 99, 1);
    private final JCheckBox shield = new JCheckBox();
    private final JTextField shieldStlFile = new JTextField();
    private final JTextField dumpX = new JTextField();
    private final JTextField dumpY = new JTextField();

    public SkirtBrimSettingsPanel() {
        addComponents(createComponents(), true);
    }

    private List<? extends JComponent> createComponents() {
        final ArrayList<JComponent> result = new ArrayList<>();
        result.add(createSkirtBox());
        result.add(createBrimBox());
        result.add(createShieldBox());
        return result;
    }

    private SettingsBoxPanel createSkirtBox() {
        final SettingsBoxPanel skirtBox = new SettingsBoxPanel("Skirt");
        skirtBox.addRow(new JLabel("Print skirt: "), skirt);
        return skirtBox;
    }

    private SettingsBoxPanel createBrimBox() {
        final SettingsBoxPanel box = new SettingsBoxPanel("Brim");
        box.addRow(new JLabel("Brim lines: "), new JSpinner(brimLines));
        return box;
    }

    private SettingsBoxPanel createShieldBox() {
        final SettingsBoxPanel box = new SettingsBoxPanel("Shield");
        box.addRow(new JLabel("Print shield: "), shield);
        box.addRow(new JLabel("Shield STL file name: "), shieldStlFile);
        box.addRow(new JLabel("Dump x coordinate: "), dumpX);
        box.addRow(new JLabel("Dump y coordinate: "), dumpY);
        return box;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Skirt and Brim";
    }

    @Override
    void setValues(final PrintSetting printSetting) {
        skirt.setSelected(printSetting.printSkirt());
        brimLines.setValue(Integer.valueOf(printSetting.getBrimLines()));
        shield.setSelected(printSetting.printShield());
        shieldStlFile.setText(printSetting.getShieldStlFile().getName());
        dumpX.setText(Integer.toString(printSetting.getDumpX()));
        dumpY.setText(Integer.toString(printSetting.getDumpY()));
    }

    @Override
    void getValues(final PrintSetting printSetting) {
        printSetting.setSkirt(skirt.isSelected());
        printSetting.setBrimLines(((Integer) brimLines.getValue()).intValue());
        printSetting.setShield(shield.isSelected());
        printSetting.setShieldStlFile(shieldStlFile.getText());
        printSetting.setDumpX(Integer.parseInt(dumpX.getText()));
        printSetting.setDumpY(Integer.parseInt(dumpY.getText()));
    }

}
