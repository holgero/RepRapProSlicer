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
import org.reprap.configuration.NamedSetting;

abstract class AbstractCategoryPanel extends AbstractSettingPanel {

    private final JComboBox<String> printCombo = new JComboBox<>();
    private final Action createNewAction;
    private final Action deleteAction;
    private final Set<String> toDelete = new HashSet<>();
    private final Map<String, String> toAdd = new LinkedHashMap<>();
    private final String settingName;
    private final String settingNameUpper;
    private final Icon addIcon;
    private final Icon deleteIcon;

    AbstractCategoryPanel(final String settingName, final String settingNameUpper, final Icon addIcon, final Icon deleteIcon) {
        this.settingName = settingName;
        this.settingNameUpper = settingNameUpper;
        this.addIcon = addIcon;
        this.deleteIcon = deleteIcon;
        createNewAction = createNewAction();
        deleteAction = createDeleteAction();
        addComponents(getFormComponents(), true);
    }

    private AbstractAction createDeleteAction() {
        return new AbstractAction("Delete " + settingNameUpper, deleteIcon) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (printCombo.getItemCount() <= 1) {
                    JOptionPane.showMessageDialog(null, "You cannot delete the last " + settingName, "Only One "
                            + settingNameUpper + " Left", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                final String name = (String) printCombo.getSelectedItem();
                toDelete.add(name);
                printCombo.removeItem(name);
            }
        };
    }

    private AbstractAction createNewAction() {
        return new AbstractAction("Create new " + settingNameUpper, addIcon) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final String name = JOptionPane.showInputDialog("Select a name for the new " + settingName,
                        printCombo.getSelectedItem());
                if (name != null && !name.isEmpty()) {
                    if (comboContains(name)) {
                        JOptionPane.showMessageDialog(null, "A " + settingName + " with the same name already exists",
                                "Duplicate " + settingNameUpper + " Name", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    toAdd.put(name, (String) printCombo.getSelectedItem());
                    toDelete.remove(name);
                    printCombo.addItem(name);
                }
            }

            private boolean comboContains(final String name) {
                for (int i = 0; i < printCombo.getItemCount(); i++) {
                    if (printCombo.getItemAt(i).equals(name)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private List<? extends JComponent> getFormComponents() {
        final List<JComponent> result = new ArrayList<>();
        final SettingsBoxPanel panel = new SettingsBoxPanel(settingNameUpper);
        panel.addRow(new JLabel("Current " + settingName + ": "), printCombo);
        panel.addRow(new JLabel("Actions: "), new JButton(createNewAction), new JButton(deleteAction));
        result.add(panel);
        return result;
    }

    void setValues(final String currentSetting, final List<? extends NamedSetting> settings) {
        printCombo.setModel(new DefaultComboBoxModel<String>(Configuration.getNames(settings)));
        toAdd.clear();
        toDelete.clear();
        printCombo.setSelectedItem(currentSetting);
    }

    String getSelectedSetting() {
        return (String) printCombo.getSelectedItem();
    }

    void performAdditions(final Configuration configuration, final Class<? extends NamedSetting> clazz) {
        for (final String newName : toAdd.keySet()) {
            final String basedOn = toAdd.get(newName);
            configuration.createAndAddSettingsCopy(newName, basedOn, clazz);
        }
        toAdd.clear();
    }

    void performDeletions(final List<? extends NamedSetting> settings) {
        for (final Iterator<? extends NamedSetting> iterator = settings.iterator(); iterator.hasNext();) {
            final NamedSetting printSetting = iterator.next();
            if (toDelete.contains(printSetting.getName())) {
                iterator.remove();
            }
        }
        toDelete.clear();
    }
}
