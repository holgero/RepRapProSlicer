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

import java.util.Collections;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.reprap.configuration.Configuration;

class DummySettingsPanel extends AbstractSettingsPanel {
    private static final Icon DEFAULT_ICON = new ImageIcon(DummySettingsPanel.class.getClassLoader().getResource(
            "icons/wrench.png"));
    private final String title;
    private final Icon icon = DEFAULT_ICON;

    DummySettingsPanel(final String title) {
        this.title = title;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    List<? extends JComponent> getFormComponents() {
        final JPanel box = new JPanel();
        box.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), title));
        box.add(new JLabel(title));
        return Collections.singletonList(box);
    }

    @Override
    public void setValues(final Configuration configuration) {
    }

    @Override
    public void getValues(final Configuration configuration) {
    }
}
