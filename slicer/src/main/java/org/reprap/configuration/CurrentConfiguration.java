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
package org.reprap.configuration;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

public class CurrentConfiguration {
    @XmlElement
    @XmlIDREF
    private PrintSetting printSetting;
    @XmlElement
    @XmlIDREF
    private PrinterSetting printerSetting;

    CurrentConfiguration() {
        this(null, null);
    }

    CurrentConfiguration(final PrintSetting printSetting, final PrinterSetting printerSetting) {
        setPrintSettings(printSetting);
        setPrinterSetting(printerSetting);
    }

    public PrintSetting getPrintSetting() {
        return printSetting;
    }

    void setPrintSettings(final PrintSetting printSetting) {
        this.printSetting = printSetting;
    }

    public PrinterSetting getPrinterSetting() {
        return printerSetting;
    }

    void setPrinterSetting(final PrinterSetting printerSetting) {
        this.printerSetting = printerSetting;
    }

    public ExtruderSetting getExtruderSetting(final String materialName) {
        for (final ExtruderSetting extruderSettings : printerSetting.getExtruderSettings()) {
            if (extruderSettings.getMaterial().getName().equals(materialName)) {
                return extruderSettings;
            }
        }
        return null;
    }
}
