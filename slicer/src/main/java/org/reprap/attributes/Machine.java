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
package org.reprap.attributes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.reprap.utilities.Debug;

public final class Machine {
    private static final String MACHINE_FILE = "Machine";
    private static Machine activeMachine = null;

    static synchronized String getActiveMachine() {
        if (activeMachine == null) {
            findActiveMachine();
        }
        if (activeMachine == null) {
            Debug.getInstance().errorMessage(
                    "No active RepRap set (add '*' to the start of a line in the file: " + getMachineFile() + ").");
        }
        return activeMachine.getName();
    }

    private static void findActiveMachine() {
        for (final Machine machine : loadMachines()) {
            if (machine.isActive()) {
                activeMachine = machine;
                break;
            }
        }
    }

    private static List<Machine> loadMachines() {
        final ArrayList<Machine> list = new ArrayList<Machine>();
        final File machineFile = getMachineFile();
        try {
            final BufferedReader reader = new BufferedReader(new FileReader(machineFile));
            try {
                do {
                    final String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    list.add(parseMachineLine(line));
                } while (true);
            } finally {
                reader.close();
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private static File getMachineFile() {
        return new File(new File(FileUtils.getUserDirectory(), Preferences.PROPERTIES_FOLDER), MACHINE_FILE);
    }

    private static Machine parseMachineLine(final String line) {
        if (line.charAt(0) == '*') {
            return new Machine(line.substring(1), true);
        }
        return new Machine(line, false);
    }

    private final String name;
    private final boolean isActive;

    Machine(final String name, final boolean isActive) {
        super();
        this.name = name;
        this.isActive = isActive;
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return isActive;
    }
}
