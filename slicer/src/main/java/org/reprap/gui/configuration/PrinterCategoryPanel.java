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

import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.PrinterSetting;

public class PrinterCategoryPanel implements SettingsNode {
    private static final Icon ICON = new ImageIcon(PrinterCategoryPanel.class.getClassLoader().getResource(
            "icons/printer_empty.png"));

    private final JComboBox<String> printerCombo = new JComboBox<>();

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Printer Settings";
    }

    @Override
    public List<? extends JComponent> getFormComponents() {
        final List<JComponent> result = new ArrayList<>();
        final SettingsBoxPanel panel = new SettingsBoxPanel("Printer");
        panel.addRow(new JLabel("Printer setting: "), printerCombo);
        result.add(panel);
        return result;
    }

    @Override
    public void setValues(final Configuration configuration) {
        printerCombo.setModel(new DefaultComboBoxModel<String>(Configuration.getNames(configuration.getPrinterSettings())));
        printerCombo.setSelectedItem(configuration.getCurrentConfiguration().getPrinterSetting().getName());
    }

    @Override
    public void getValues(final Configuration configuration) {
        final String printer = (String) printerCombo.getSelectedItem();
        for (final PrinterSetting setting : configuration.getPrinterSettings()) {
            if (setting.getName().equals(printer)) {
                configuration.getCurrentConfiguration().setPrinterSetting(setting);
                return;
            }
        }
        throw new IllegalStateException("Unknown printer setting >>" + printer + "<< in combo box.");
    }

}
