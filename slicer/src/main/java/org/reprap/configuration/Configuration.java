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
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.io.FileUtils;

@XmlRootElement
public class Configuration {
    public static final File REPRAP_DIRECTORY = new File(FileUtils.getUserDirectory(), ".reprap");

    @XmlElementWrapper
    @XmlElement(name = "material")
    private List<MaterialSetting> materials;
    @XmlElementWrapper
    @XmlElement(name = "printSetting")
    private List<PrintSetting> printSettings;
    @XmlElementWrapper
    @XmlElement(name = "printerSetting")
    private List<PrinterSetting> printerSettings;
    @XmlElement
    private CurrentConfiguration currentConfiguration;

    public Configuration() {
    }

    @XmlTransient
    public List<PrintSetting> getPrintSettings() {
        return printSettings;
    }

    public void setPrintSettings(final List<PrintSetting> printSettings) {
        this.printSettings = printSettings;
    }

    @XmlTransient
    public List<PrinterSetting> getPrinterSettings() {
        return printerSettings;
    }

    public void setPrinterSettings(final List<PrinterSetting> printerSettings) {
        this.printerSettings = printerSettings;
    }

    @XmlTransient
    public CurrentConfiguration getCurrentConfiguration() {
        return currentConfiguration;
    }

    public void setCurrentConfiguration(final CurrentConfiguration currentConfiguration) {
        this.currentConfiguration = currentConfiguration;
    }

    @XmlTransient
    public List<MaterialSetting> getMaterials() {
        return materials;
    }

    public void setMaterials(final List<MaterialSetting> materials) {
        this.materials = materials;
    }

    public static String[] getNames(final List<? extends NamedSetting> namedSetting) {
        final List<String> settingsNames = new ArrayList<>();
        for (final NamedSetting setting : namedSetting) {
            settingsNames.add(setting.getName());
        }
        return settingsNames.toArray(new String[settingsNames.size()]);
    }

    public <T extends NamedSetting> T findSetting(final String name, final Class<T> clazz) {
        final List<? extends NamedSetting> settings;
        if (clazz == PrinterSetting.class) {
            settings = printerSettings;
        } else if (clazz == PrintSetting.class) {
            settings = printSettings;
        } else if (clazz == MaterialSetting.class) {
            settings = materials;
        } else {
            throw new IllegalArgumentException("unknown class: " + clazz);
        }
        for (final NamedSetting setting : settings) {
            if (setting.getName().equals(name)) {
                return clazz.cast(setting);
            }
        }
        return null;
    }

    public <T extends NamedSetting> void createAndAddSettingsCopy(final String newName, final String basedOn,
            final Class<T> clazz) {
        final NamedSetting baseSetting = findSetting(basedOn, clazz);
        if (baseSetting == null) {
            throw new IllegalStateException("Unknown setting >>" + baseSetting + "<<.");
        }
        if (baseSetting instanceof PrinterSetting) {
            printerSettings.add(new PrinterSetting(newName, (PrinterSetting) baseSetting));
        } else if (baseSetting instanceof PrintSetting) {
            printSettings.add(new PrintSetting(newName, (PrintSetting) baseSetting));
        } else if (baseSetting instanceof MaterialSetting) {
            materials.add(new MaterialSetting(newName, (MaterialSetting) baseSetting));
        }
    }

}
