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

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class CurrentConfiguration {
    @XmlElement
    private PrintSettings printSettings;
    @XmlElement
    private PrinterSettings printerSettings;

    CurrentConfiguration() {
        this(null, null);
    }

    CurrentConfiguration(final PrintSettings printSettings, final PrinterSettings printerSettings) {
        setPrintSettings(printSettings);
        setPrinterSettings(printerSettings);
    }

    void writeToXml(final File file) {
        try {
            final JAXBContext context = JAXBContext.newInstance(CurrentConfiguration.class);
            final Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(this, file);
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public PrintSettings getPrintSettings() {
        return printSettings;
    }

    void setPrintSettings(final PrintSettings printSettings) {
        this.printSettings = printSettings;
    }

    public PrinterSettings getPrinterSettings() {
        return printerSettings;
    }

    void setPrinterSettings(final PrinterSettings printerSettings) {
        this.printerSettings = printerSettings;
    }

    public ExtruderSettings getExtruderSettings(final String materialName) {
        for (final ExtruderSettings extruderSettings : printerSettings.getExtruderSettings()) {
            if (extruderSettings.getMaterial().getName().equals(materialName)) {
                return extruderSettings;
            }
        }
        return null;
    }
}
