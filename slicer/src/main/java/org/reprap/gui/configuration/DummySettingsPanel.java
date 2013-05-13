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

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

class DummySettingsPanel extends JPanel {
    private static final Icon DEFAULT_ICON = new ImageIcon(DummySettingsPanel.class.getClassLoader().getResource(
            "icons/wrench.png"));
    private final String title;
    private final Icon icon = DEFAULT_ICON;

    DummySettingsPanel(final String title) {
        this.title = title;
    }

    String getTitle() {
        return title;
    }

    Icon getIcon() {
        return icon;
    }
}
