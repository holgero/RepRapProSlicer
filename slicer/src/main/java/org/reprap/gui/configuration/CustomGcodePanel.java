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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.PrinterSetting;

public class CustomGcodePanel implements SettingsNode {
    private static final Icon ICON = new ImageIcon(CustomGcodePanel.class.getClassLoader().getResource("icons/script.png"));
    private final JLabel printerSettingName = new JLabel();
    private final JTextArea prologueTextArea = new JTextArea(2, 10);
    private final JTextArea epilogueTextArea = new JTextArea(2, 10);

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Custom G-Code";
    }

    private List<? extends JComponent> getFormComponents() {
        final List<JComponent> result = new ArrayList<>();
        result.add(createTextAreaScrollPane(prologueTextArea, "Prologue"));
        result.add(createTextAreaScrollPane(epilogueTextArea, "Epilogue"));
        return result;
    }

    private static JScrollPane createTextAreaScrollPane(final JTextArea textArea, final String title) {
        textArea.setLineWrap(true);
        final JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), title));
        return scrollPane;
    }

    @Override
    public void setValues(final Configuration configuration) {
        setValues(configuration.getCurrentConfiguration().getPrinterSetting());
    }

    public void setValues(final PrinterSetting printerSetting) {
        printerSettingName.setText(printerSetting.getName());
        prologueTextArea.setText(printerSetting.getGcodePrologue());
        epilogueTextArea.setText(printerSetting.getGcodeEpilogue());
    }

    @Override
    public void getValues(final Configuration configuration) {
        getValues(configuration.getCurrentConfiguration().getPrinterSetting());
    }

    private void getValues(final PrinterSetting printerSetting) {
        if (!printerSetting.getName().equals(printerSettingName.getText())) {
            throw new IllegalStateException("My printer setting is " + printerSettingName.getText()
                    + ", but current printer setting is " + printerSetting.getName() + ".");
        }
        printerSetting.setGcodePrologue(prologueTextArea.getText());
        printerSetting.setGcodeEpilogue(epilogueTextArea.getText());
    }

    @Override
    public JPanel getPanel() {
        final JPanel result = new JPanel();
        result.setLayout(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 0;
        final JPanel printerNamePanel = new JPanel();
        printerNamePanel.add(printerSettingName);
        result.add(printerNamePanel, constraints);
        constraints.gridy++;
        constraints.weighty = 1.0;
        for (final JComponent component : getFormComponents()) {
            result.add(component, constraints);
            constraints.gridy++;
        }

        return result;
    }

}
