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
import org.reprap.configuration.PrintSetting;

public class PrintCategoryPanel extends AbstractSettingPanel {
    private static final Icon ICON = createIcon("wrench.png");

    private final JComboBox<String> printCombo = new JComboBox<>();
    private final Action createNewAction;
    private final Action deleteAction;
    private final Set<String> toDelete = new HashSet<>();
    private final Map<String, String> toAdd = new LinkedHashMap<>();

    PrintCategoryPanel() {
        createNewAction = new AbstractAction("Create new Print Setting", createIcon("add.png")) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final String name = JOptionPane.showInputDialog("Select a name for the new print setting",
                        printCombo.getSelectedItem());
                if (name != null && !name.isEmpty()) {
                    if (printComboContains(name)) {
                        JOptionPane.showMessageDialog(null, "A print setting with the same name already exists",
                                "Duplicate Print Setting Name", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    toAdd.put(name, (String) printCombo.getSelectedItem());
                    toDelete.remove(name);
                    printCombo.addItem(name);
                }
            }

            private boolean printComboContains(final String name) {
                for (int i = 0; i < printCombo.getItemCount(); i++) {
                    if (printCombo.getItemAt(i).equals(name)) {
                        return true;
                    }
                }
                return false;
            }
        };
        deleteAction = new AbstractAction("Delete Print Setting", createIcon("delete.png")) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (printCombo.getItemCount() <= 1) {
                    JOptionPane.showMessageDialog(null, "You cannot delete the last print setting",
                            "Only One Print Setting Left", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                final String name = (String) printCombo.getSelectedItem();
                toDelete.add(name);
                printCombo.removeItem(name);
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
        return "Print Settings";
    }

    private List<? extends JComponent> getFormComponents() {
        final List<JComponent> result = new ArrayList<>();
        final SettingsBoxPanel panel = new SettingsBoxPanel("Print Setting");
        panel.addRow(new JLabel("Current print setting: "), printCombo);
        panel.addRow(new JLabel("Actions: "), new JButton(createNewAction), new JButton(deleteAction));
        result.add(panel);
        return result;
    }

    @Override
    public void setValues(final Configuration configuration) {
        printCombo.setModel(new DefaultComboBoxModel<String>(Configuration.getNames(configuration.getPrintSettings())));
        toDelete.clear();
        printCombo.setSelectedItem(configuration.getCurrentConfiguration().getPrintSetting().getName());
    }

    @Override
    public void getValues(final Configuration configuration) {
        performAdditions(configuration);
        performDeletions(configuration);
        final String printSetting = (String) printCombo.getSelectedItem();
        final PrintSetting setting = configuration.findPrintSetting(printSetting);
        if (setting == null) {
            throw new IllegalStateException("Unknown print setting >>" + printSetting + "<< in combo box.");
        }
        configuration.getCurrentConfiguration().setPrintSetting(setting);
    }

    private void performAdditions(final Configuration configuration) {
        for (final String newName : toAdd.keySet()) {
            final String basedOn = toAdd.get(newName);
            configuration.getPrintSettings().add(configuration.createPrintSettingsCopy(newName, basedOn));
        }
        toAdd.clear();
    }

    private void performDeletions(final Configuration configuration) {
        final List<PrintSetting> printSettings = configuration.getPrintSettings();
        for (final Iterator<PrintSetting> iterator = printSettings.iterator(); iterator.hasNext();) {
            final PrintSetting printSetting = iterator.next();
            if (toDelete.contains(printSetting.getName())) {
                iterator.remove();
            }
        }
        toDelete.clear();
    }
}
