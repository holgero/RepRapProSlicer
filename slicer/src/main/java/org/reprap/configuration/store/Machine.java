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
package org.reprap.configuration.store;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

final class Machine {
    private static final String PROPERTIES_FOLDER = ".reprap";
    private static final String MACHINE_FILE = "Machine";

    static List<Machine> getMachines() {
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
        return new File(new File(FileUtils.getUserDirectory(), PROPERTIES_FOLDER), MACHINE_FILE);
    }

    private static Machine parseMachineLine(final String line) {
        if (line.charAt(0) == '*') {
            return new Machine(line.substring(1), true);
        }
        return new Machine(line, false);
    }

    private final String name;
    private final boolean isActive;

    private Machine(final String name, final boolean isActive) {
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
