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

import java.awt.BorderLayout;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.reprap.configuration.FillPattern;
import org.reprap.configuration.LinearFillPattern;
import org.reprap.configuration.RectilinearFillPattern;

public class FillPatternControl extends JComponent {
    private static final String RECTILINEAR = "rectilinear";
    private static final String LINEAR = "linear";
    private final JComboBox<String> comboBox = new JComboBox<>(new String[] { RECTILINEAR, LINEAR });
    private final JTextField angle = new JTextField();

    public FillPatternControl() {
        setLayout(new BorderLayout(5, 1));
        add(comboBox, BorderLayout.WEST);
        add(new JLabel(" angle: "), BorderLayout.CENTER);
        add(angle, BorderLayout.EAST);
    }

    public void setValues(final FillPattern pattern) {
        if (pattern instanceof RectilinearFillPattern) {
            comboBox.setSelectedItem(RECTILINEAR);
            angle.setText(Double.toString(((RectilinearFillPattern) pattern).getFillAngle()));
        } else if (pattern instanceof LinearFillPattern) {
            comboBox.setSelectedItem(LINEAR);
            angle.setText(Double.toString(((LinearFillPattern) pattern).getFillAngle()));
        }
    }

    public FillPattern getValue() {
        final double angleValue = Double.parseDouble(angle.getText());
        switch ((String) comboBox.getSelectedItem()) {
        case RECTILINEAR:
            return new RectilinearFillPattern(angleValue);
        case LINEAR:
            return new LinearFillPattern(angleValue);
        default:
            throw new IllegalStateException("Illegal fill pattern name: " + comboBox.getSelectedItem());
        }
    }
}
