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
import java.util.Collections;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.reprap.configuration.Configuration;
import org.reprap.configuration.PrintSetting;

abstract class AbstractPrintSettingPanel extends AbstractSettingPanel {
    private final JLabel printSettingName = new JLabel();

    public AbstractPrintSettingPanel() {
        final GridBagConstraints constraints = getConstraints();
        constraints.weighty = 0;
        addComponents(Collections.singletonList(createNamePanel()), false);
        constraints.weighty = 1;
    }

    @Override
    public final void setValues(final Configuration configuration) {
        final PrintSetting printSetting = configuration.getCurrentConfiguration().getPrintSetting();
        printSettingName.setText(printSetting.getName());
        setValues(printSetting);
    }

    @Override
    public final void getValues(final Configuration configuration) {
        final PrintSetting printSetting = configuration.getCurrentConfiguration().getPrintSetting();
        if (!printSetting.getName().equals(printSettingName.getText())) {
            throw new IllegalStateException("My print setting is " + printSettingName.getText()
                    + ", but current print setting is " + printSetting.getName() + ".");
        }
        getValues(printSetting);
    }

    private JComponent createNamePanel() {
        final JPanel result = new JPanel();
        result.add(printSettingName);
        return result;
    }

    abstract void setValues(PrintSetting printSetting);

    abstract void getValues(PrintSetting printSetting);
}
