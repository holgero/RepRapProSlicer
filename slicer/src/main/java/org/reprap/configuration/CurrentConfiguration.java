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

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlIDREF;

public class CurrentConfiguration {
    private PrintSetting printSetting;
    private PrinterSetting printerSetting;
    @XmlElementWrapper
    @XmlElement(name = "material")
    @XmlIDREF
    private final List<MaterialSetting> materials;

    CurrentConfiguration() {
        this(null, null, null);
    }

    public CurrentConfiguration(final PrintSetting printSetting, final PrinterSetting printerSetting,
            final List<MaterialSetting> materials) {
        this.printSetting = printSetting;
        this.printerSetting = printerSetting;
        this.materials = materials;
    }

    @XmlElement
    @XmlIDREF
    public PrintSetting getPrintSetting() {
        return printSetting;
    }

    public void setPrintSetting(final PrintSetting printSetting) {
        this.printSetting = printSetting;
    }

    @XmlElement
    @XmlIDREF
    public PrinterSetting getPrinterSetting() {
        return printerSetting;
    }

    public void setPrinterSetting(final PrinterSetting printerSetting) {
        this.printerSetting = printerSetting;
    }

    public ExtruderSetting getExtruderSetting(final String materialName) {
        for (int i = 0; i < materials.size(); i++) {
            if (materials.get(i).getName().equals(materialName)) {
                return printerSetting.getExtruderSettings().get(i);
            }
        }
        return null;
    }

    public List<MaterialSetting> getMaterials() {
        return Collections.unmodifiableList(materials);
    }

    public void setMaterial(final int index, final MaterialSetting materialSetting) {
        materials.set(index, materialSetting);
    }
}
