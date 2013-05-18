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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.reprap.configuration.FillPattern;
import org.reprap.configuration.LinearFillPattern;
import org.reprap.configuration.PrintSetting;
import org.reprap.configuration.RectilinearFillPattern;
import org.reprap.gui.configuration.common.SettingsBoxPanel;

public class InfillSettingsPanel extends AbstractPrintSettingPanel {
    private static final Icon ICON = createIcon("paintcan.png");
    private final JTextField fillDensity = new JTextField();
    private final JComboBox<String> pattern = new JComboBox<String>(new String[] { "rectilinear", "linear" });
    private final JTextField fillAngle = new JTextField();
    private final JTextField infillOverlap = new JTextField();

    public InfillSettingsPanel() {
        addComponents(createComponents(), true);
    }

    private List<? extends JComponent> createComponents() {
        final ArrayList<JComponent> result = new ArrayList<>();
        result.add(createInfillBox());
        return result;
    }

    private SettingsBoxPanel createInfillBox() {
        final SettingsBoxPanel result = new SettingsBoxPanel("Infill");
        result.addRow(new JLabel("Fill density (%): "), fillDensity);
        result.addRow(new JLabel("Fill pattern: "), pattern);
        result.addRow(new JLabel("Fill angle (degrees): "), fillAngle);
        result.addRow(new JLabel("Overlapp (mm): "), infillOverlap);
        return result;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Infill";
    }

    @Override
    void setValues(final PrintSetting printSetting) {
        fillDensity.setText(Double.toString(printSetting.getFillDensity()));
        final FillPattern fillPattern = printSetting.getFillPattern();
        if (fillPattern instanceof RectilinearFillPattern) {
            pattern.setSelectedItem("rectilinear");
            fillAngle.setText(Double.toString(((RectilinearFillPattern) fillPattern).getFillAngle()));
        } else if (fillPattern instanceof LinearFillPattern) {
            pattern.setSelectedItem("linear");
            fillAngle.setText(Double.toString(((LinearFillPattern) fillPattern).getFillAngle()));
        }
        infillOverlap.setText(Double.toString(printSetting.getInfillOverlap()));
    }

    @Override
    void getValues(final PrintSetting printSetting) {
        printSetting.setFillDensity(fieldToDouble(fillDensity));
        final double fillAngleValue = fieldToDouble(fillAngle);
        switch ((String) pattern.getSelectedItem()) {
        case "rectilinear":
            printSetting.setFillPattern(new RectilinearFillPattern(fillAngleValue));
            break;
        case "linear":
            printSetting.setFillPattern(new LinearFillPattern(fillAngleValue));
            break;
        default:
            throw new IllegalStateException("Illegal fill pattern name: " + pattern.getSelectedItem());
        }
        printSetting.setInfillOverlap(fieldToDouble(infillOverlap));
    }
}
