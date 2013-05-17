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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.reprap.configuration.PrinterSetting;

class CustomGcodePanel extends AbstractPrinterSettingPanel {
    private static final Icon ICON = new ImageIcon(CustomGcodePanel.class.getClassLoader().getResource("icons/script.png"));
    private final JTextArea prologueTextArea = new JTextArea(2, 10);
    private final JTextArea epilogueTextArea = new JTextArea(2, 10);

    CustomGcodePanel() {
        addComponents(getFormComponents(), false);
    }

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
    void setValues(final PrinterSetting printerSetting) {
        prologueTextArea.setText(printerSetting.getGcodePrologue());
        epilogueTextArea.setText(printerSetting.getGcodeEpilogue());
    }

    @Override
    void getValues(final PrinterSetting printerSetting) {
        printerSetting.setGcodePrologue(prologueTextArea.getText());
        printerSetting.setGcodeEpilogue(epilogueTextArea.getText());
    }
}
