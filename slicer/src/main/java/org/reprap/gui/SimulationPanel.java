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
package org.reprap.gui;

import java.awt.BorderLayout;
import java.awt.Window;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.geometry.SimulationPlotter;

class SimulationPanel extends JPanel {
    private final SimulationPlotter simulationPlotter;
    private final SimulationPlotter.MouseClickMagnifier mouseListener;
    private final SimulationPlotter.TogglePlotBox togglePlotBox;

    SimulationPanel(final CurrentConfiguration configuration) {
        setLayout(new BorderLayout());
        setFocusable(true);
        simulationPlotter = new SimulationPlotter(configuration);
        mouseListener = simulationPlotter.new MouseClickMagnifier();
        togglePlotBox = simulationPlotter.new TogglePlotBox();
        add(simulationPlotter, BorderLayout.CENTER);
    }

    void hookListeners(final boolean activate) {
        final Window window = SwingUtilities.getWindowAncestor(this);
        if (activate) {
            addMouseListener(mouseListener);
            window.addKeyListener(togglePlotBox);
        } else {
            removeMouseListener(mouseListener);
            window.removeKeyListener(togglePlotBox);
        }
    }

    SimulationPlotter getSimulationPlotter() {
        return simulationPlotter;
    }
}
