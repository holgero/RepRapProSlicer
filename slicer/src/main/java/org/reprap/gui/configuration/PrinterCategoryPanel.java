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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.PrinterSetting;

public class PrinterCategoryPanel extends AbstractSettingPanel {
    private static final Icon ICON = createIcon("printer_empty.png");

    private final JComboBox<String> printerCombo = new JComboBox<>();
    private final Action createNewAction;
    private final Action deleteAction;
    private final Set<String> toDelete = new HashSet<>();
    private final Map<String, String> toAdd = new LinkedHashMap<>();

    PrinterCategoryPanel() {
        createNewAction = new AbstractAction("Create new Printer", createIcon("printer_add.png")) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final String name = JOptionPane.showInputDialog("Select a name for the new printer setting",
                        printerCombo.getSelectedItem());
                if (name != null && !name.isEmpty()) {
                    if (printerComboContains(name)) {
                        JOptionPane.showMessageDialog(null, "A printer with the same name already exists",
                                "Duplicate Printer Name", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    toAdd.put(name, (String) printerCombo.getSelectedItem());
                    toDelete.remove(name);
                    printerCombo.addItem(name);
                }
            }

            private boolean printerComboContains(final String name) {
                for (int i = 0; i < printerCombo.getItemCount(); i++) {
                    if (printerCombo.getItemAt(i).equals(name)) {
                        return true;
                    }
                }
                return false;
            }
        };
        deleteAction = new AbstractAction("Delete Printer", createIcon("printer_delete.png")) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (printerCombo.getItemCount() <= 1) {
                    JOptionPane.showMessageDialog(null, "You cannot delete the last printer", "Only One Printer Left",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                final String name = (String) printerCombo.getSelectedItem();
                toDelete.add(name);
                printerCombo.removeItem(name);
            }
        };
        addComponents(getFormComponents(), true);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Printer Settings";
    }

    private List<? extends JComponent> getFormComponents() {
        final List<JComponent> result = new ArrayList<>();
        final SettingsBoxPanel panel = new SettingsBoxPanel("Printer");
        panel.addRow(new JLabel("Current printer: "), printerCombo);
        panel.addRow(new JLabel("Actions: "), new JButton(createNewAction), new JButton(deleteAction));
        result.add(panel);
        return result;
    }

    @Override
    public void setValues(final Configuration configuration) {
        printerCombo.setModel(new DefaultComboBoxModel<String>(Configuration.getNames(configuration.getPrinterSettings())));
        toDelete.clear();
        printerCombo.setSelectedItem(configuration.getCurrentConfiguration().getPrinterSetting().getName());
    }

    @Override
    public void getValues(final Configuration configuration) {
        performAdditions(configuration);
        performDeletions(configuration);
        final String printer = (String) printerCombo.getSelectedItem();
        final PrinterSetting setting = configuration.findPrinterSetting(printer);
        if (setting == null) {
            throw new IllegalStateException("Unknown printer setting >>" + printer + "<< in combo box.");
        }
        configuration.getCurrentConfiguration().setPrinterSetting(setting);
    }

    private void performAdditions(final Configuration configuration) {
        for (final String newName : toAdd.keySet()) {
            final String basedOn = toAdd.get(newName);
            configuration.getPrinterSettings().add(configuration.createSettingsCopy(newName, basedOn));
        }
        toAdd.clear();
    }

    private void performDeletions(final Configuration configuration) {
        final List<PrinterSetting> printerSettings = configuration.getPrinterSettings();
        for (final Iterator<PrinterSetting> iterator = printerSettings.iterator(); iterator.hasNext();) {
            final PrinterSetting printerSetting = iterator.next();
            if (toDelete.contains(printerSetting.getName())) {
                iterator.remove();
            }
        }
        toDelete.clear();
    }
}
