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

public class SupportSettingPanel extends AbstractPrintSettingPanel {
    private static final Icon ICON = createIcon("shape_align_top.png");

    private final JCheckBox support = new JCheckBox();
    private final SpinnerNumberModel supportExtruder = new SpinnerNumberModel(1, 1, 99, 1);
    private final FillPatternControl supportPattern = new FillPatternControl();
    private final JTextField supportSpacing = new JTextField();
    private final SpinnerNumberModel raftLayers = new SpinnerNumberModel(0, 0, 99, 1);

    public SupportSettingPanel() {
        addComponents(createComponents(), true);
    }

    private List<? extends JComponent> createComponents() {
        final ArrayList<JComponent> result = new ArrayList<>();
        result.add(createSupportBox());
        result.add(createRaftBox());
        return result;
    }

    private SettingsBoxPanel createSupportBox() {
        final SettingsBoxPanel box = new SettingsBoxPanel("Support");
        box.addRow(new JLabel("Generate support: "), support);
        box.addRow(new JLabel("Support extruder: "), new JSpinner(supportExtruder));
        box.addRow(new JLabel("Support pattern: "), supportPattern);
        box.addRow(new JLabel("Support spacing: "), supportSpacing);
        return box;
    }

    private JComponent createRaftBox() {
        final SettingsBoxPanel box = new SettingsBoxPanel("Raft");
        box.addRow(new JLabel("Raft layers: "), new JSpinner(raftLayers));
        return box;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Support material";
    }

    @Override
    void setValues(final PrintSetting printSetting) {
        support.setSelected(printSetting.printSupport());
        supportExtruder.setValue(Integer.valueOf(printSetting.getSupportExtruder() + 1));
        supportPattern.setValues(printSetting.getSupportPattern());
        supportSpacing.setText(Double.toString(printSetting.getSupportSpacing()));
        raftLayers.setValue(Integer.valueOf(printSetting.getRaftLayers()));
    }

    @Override
    void getValues(final PrintSetting printSetting) {
        printSetting.setSupport(support.isSelected());
        printSetting.setSupportExtruder(((Integer) supportExtruder.getValue()).intValue() - 1);
        printSetting.setSupportPattern(supportPattern.getValue());
        printSetting.setSupportSpacing(fieldToDouble(supportSpacing));
        printSetting.setRaftLayers(((Integer) raftLayers.getValue()).intValue());
    }

}
