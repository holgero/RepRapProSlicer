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

import org.reprap.configuration.Configuration;

public class ExtruderPanel extends AbstractSettingsPanel {
    private static final Icon ICON = new ImageIcon(ExtruderPanel.class.getClassLoader().getResource("icons/funnel.png"));
    private final int number;

    public ExtruderPanel(final int number) {
        this.number = number;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Extruder " + number;
    }

    @Override
    List<? extends JComponent> getFormComponents() {
        // TODO Auto-generated method stub
        return Collections.emptyList();
    }

    @Override
    public void setValues(final Configuration configuration) {
        // TODO Auto-generated method stub
    }

    @Override
    public void getValues(final Configuration configuration) {
        // TODO Auto-generated method stub
    }

}
