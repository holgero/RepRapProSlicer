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

import java.io.File;

import org.reprap.geometry.ProductionProgressListener;
import org.reprap.geometry.SimulationPlotter;

public class StatusBarUpdater implements ProductionProgressListener {

    private final StatusBar statusBar;
    private final File currentFile;
    private final SimulationPlotter simulationPlotter;

    public StatusBarUpdater(final StatusBar statusBar, final File currentFile, final SimulationPlotter simulationPlotter) {
        this.statusBar = statusBar;
        this.currentFile = currentFile;
        this.simulationPlotter = simulationPlotter;
    }

    @Override
    public void productionProgress(final int layer, final int totalLayers) {
        statusBar.setMessage("Slicing " + currentFile.getName() + ": " + layer + "/" + totalLayers
                + (simulationPlotter.isPauseSlicer() ? " (paused)" : ""));
    }
}
