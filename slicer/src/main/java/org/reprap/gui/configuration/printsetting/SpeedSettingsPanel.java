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

import javax.swing.Icon;

import org.reprap.configuration.PrintSetting;

public class SpeedSettingsPanel extends AbstractPrintSettingPanel {
    private static final Icon ICON = createIcon("time.png");

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTitle() {
        return "Speed";
    }

    @Override
    void setValues(final PrintSetting printSetting) {
        // TODO Auto-generated method stub

    }

    @Override
    void getValues(final PrintSetting printSetting) {
        // TODO Auto-generated method stub

    }

}
