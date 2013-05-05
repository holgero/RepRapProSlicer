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

import static org.reprap.configuration.MathRoutines.circleAreaForDiameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.vecmath.Color3f;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class PropertyPreferencesConverter {
    private static final Logger LOGGER = LogManager.getLogger(PropertyPreferencesConverter.class);
    private static final String SHIELD_STL_FILENAME = "shield.stl";
    private static final String PROLOGUE_FILE = "prologue.gcode";
    private static final String EPILOGUE_FILE = "epilogue.gcode";
    private static final String BASE_FILE = "base.stl";

    private final File reprapDirectory;
    private Properties properties;
    private String machineName;
    private File propertiesFile;
    private final Map<String, PrinterSetting> printerSettings = new LinkedHashMap<>();
    private final Map<String, PrintSetting> printSettings = new LinkedHashMap<>();
    private final Map<String, MaterialSetting> materialSettings = new LinkedHashMap<>();
    private final List<MaterialSetting> activeMaterials = new ArrayList<>();

    PropertyPreferencesConverter(final File reprapDirectory) {
        this.reprapDirectory = reprapDirectory;
    }

    Configuration loadConfigurationFromPropertiesFiles() {
        final CurrentConfiguration currentConfiguration = collectPropertiesOfAllMachines();
        final Configuration configuration = new Configuration();
        configuration.setCurrentConfiguration(currentConfiguration);
        configuration.setPrinterSettings(new ArrayList<>(printerSettings.values()));
        configuration.setPrintSettings(new ArrayList<>(printSettings.values()));
        configuration.setMaterials(new ArrayList<>(materialSettings.values()));
        return configuration;
    }

    private CurrentConfiguration collectPropertiesOfAllMachines() {
        CurrentConfiguration currentConfiguration = null;
        clearAll();
        final List<Machine> machines = Machine.getMachines();
        for (final Machine machine : machines) {
            machineName = machine.getName();
            propertiesFile = new File(new File(reprapDirectory, machineName), "reprap.properties");
            properties = loadProperties();
            if (properties == null) {
                continue;
            }
            final PrintSetting printSetting = createPrintSetting();
            printSettings.put(printSetting.getName(), printSetting);
            final int remaining = removeUnusedExtruders();
            final PrinterSetting printerSetting = createPrinterSetting();
            final ExtruderSetting[] extruders = createAllExtruderSettings(remaining, printSetting.getLayerHeight(),
                    machine.isActive());
            printerSetting.setExtruderSettings(extruders);
            printerSettings.put(printerSetting.getName(), printerSetting);
            if (machine.isActive()) {
                currentConfiguration = new CurrentConfiguration(printSetting, printerSetting, activeMaterials);
            }
        }
        return currentConfiguration;
    }

    private void clearAll() {
        printSettings.clear();
        printerSettings.clear();
        materialSettings.clear();
        activeMaterials.clear();
    }

    private Properties loadProperties() {
        try {
            final InputStream fileStream = new FileInputStream(propertiesFile);
            try {
                final Properties result = new Properties();
                result.load(fileStream);
                LOGGER.info("Configuration loaded from: " + propertiesFile);
                return result;
            } finally {
                fileStream.close();
            }
        } catch (final IOException e) {
            LOGGER.catching(Level.DEBUG, e);
            LOGGER.info("Failed to read current configuration: " + e);
            return null;
        }
    }

    private ExtruderSetting[] createAllExtruderSettings(final int extruderCount, final double layerHeight, final boolean active) {
        final ExtruderSetting[] extruderSettings = new ExtruderSetting[extruderCount];
        for (int i = 0; i < extruderSettings.length; i++) {
            final ExtruderSetting setting = createExtruderSetting(i, layerHeight, active);
            extruderSettings[i] = setting;
        }
        return extruderSettings;
    }

    private ExtruderSetting createExtruderSetting(final int number, final double layerHeight, final boolean active) {
        final ExtruderSetting setting = new ExtruderSetting();
        // this is wrong, but the migration from the properties must somehow guess the nozzle diameter
        final String prefix = "Extruder" + number + "_";
        setting.setNozzleDiameter(getDoubleProperty(prefix + "ExtrusionSize(mm)"));
        setting.setRetraction(toFilamentLength(getDoubleProperty(prefix + "Reverse(ms)"), number, layerHeight));
        setting.setExtraLengthPerLayer(toFilamentLength(getDoubleProperty(prefix + "ExtrusionDelayForLayer(ms)"), number,
                layerHeight));
        setting.setExtraLengthPerPolygon(toFilamentLength(getDoubleProperty(prefix + "ExtrusionDelayForPolygon(ms)"), number,
                layerHeight));
        setting.setExtrudeRatio(getDoubleProperty(prefix + "ExtrudeRatio(0..)"));
        setting.setExtrusionOverrun(getDoubleProperty(prefix + "ExtrusionOverRun(mm)"));
        setting.setAirExtrusionFeedRate(getDoubleProperty(prefix + "FastEFeedrate(mm/minute)"));
        setting.setPrintExtrusionRate(getDoubleProperty(prefix + "FastXYFeedrate(mm/minute)"));
        setting.setLift(getDoubleProperty(prefix + "Lift(mm)"));
        final MaterialSetting material = createMaterialSettings(prefix);
        materialSettings.put(material.getName(), material);
        if (active) {
            activeMaterials.add(material);
        }
        return setting;
    }

    private MaterialSetting createMaterialSettings(final String prefix) {
        final MaterialSetting material = new MaterialSetting();
        material.setName(properties.getProperty(prefix + "MaterialType(name)"));
        material.setDiameter(getDoubleProperty(prefix + "FeedDiameter(mm)"));
        material.setColor(new Color3f(loadColorComponent(prefix, "R"), loadColorComponent(prefix, "G"), loadColorComponent(
                prefix, "B")));
        return material;
    }

    private float loadColorComponent(final String prefix, final String component) {
        return (float) getDoubleProperty(prefix + "Colour" + component + "(0..1)");
    }

    private double toFilamentLength(final double delay, final int extruder, final double layerHeight) {
        final String prefix = "Extruder" + extruder + "_";
        final double extrusionSpeed = getDoubleProperty(prefix + "ExtrusionSpeed(mm/minute)");
        final double feedDiameter = getDoubleProperty(prefix + "FeedDiameter(mm)");
        final double extrusionSize = getDoubleProperty(prefix + "ExtrusionSize(mm)");
        return extrusionSpeed * delay / 60000 * layerHeight * extrusionSize / circleAreaForDiameter(feedDiameter);
    }

    private PrinterSetting createPrinterSetting() {
        final PrinterSetting result = new PrinterSetting();
        result.setName(machineName);
        result.setBedSizeX(getDoubleProperty("WorkingX(mm)"));
        result.setBedSizeY(getDoubleProperty("WorkingY(mm)"));
        result.setMaximumZ(getDoubleProperty("WorkingZ(mm)"));
        result.setRelativeDistanceE(getBooleanProperty("ExtrusionRelative"));
        result.setMaximumFeedrateX(getDoubleProperty("MaximumFeedrateX(mm/minute)"));
        result.setMaximumFeedrateY(getDoubleProperty("MaximumFeedrateY(mm/minute)"));
        result.setMaximumFeedrateZ(getDoubleProperty("MaximumFeedrateZ(mm/minute)"));
        result.setGcodePrologue(readGcodeFromFile(PROLOGUE_FILE));
        result.setGcodeEpilogue(readGcodeFromFile(EPILOGUE_FILE));
        result.setBuildPlatformStl(machineDirFileName(BASE_FILE));
        return result;
    }

    private String readGcodeFromFile(final String fileName) {
        try {
            final StringWriter text = new StringWriter();
            final File file = new File(reprapDirectory, machineDirFileName(fileName));
            try (FileReader fileReader = new FileReader(file)) {
                IOUtils.copy(fileReader, text);
            }
            return text.toString();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String machineDirFileName(final String fileName) {
        return new File(machineName, fileName).getPath();
    }

    private PrintSetting createPrintSetting() {
        final PrintSetting result = new PrintSetting();
        result.setName("Setting for " + machineName);
        result.setLayerHeight(getDoubleProperty("Extruder0_ExtrusionHeight(mm)"));
        result.setVerticalShells(getIntegerProperty("Extruder0_NumberOfShells(0..N)"));
        result.setHorizontalShells(getIntegerProperty("Extruder0_SurfaceLayers(0..N)"));
        // extruder #3 is the first infill extruder in the default configuration file
        final double fillDensity = getDoubleProperty("Extruder3_ExtrusionSize(mm)")
                / getDoubleProperty("Extruder3_ExtrusionInfillWidth(mm)");
        LOGGER.info("fill density is: " + fillDensity);
        result.setFillDensity(fillDensity);
        result.setFillPattern(new RectilinearFillPattern(getDoubleProperty("Extruder0_EvenHatchDirection(degrees)")));
        result.setPerimeterSpeed(getDoubleProperty("Extruder0_OutlineSpeed(0..1)"));
        result.setInfillSpeed(getDoubleProperty("Extruder0_InfillSpeed(0..1)"));
        result.setSkirt(getBooleanProperty("StartRectangle"));
        if (properties.getProperty("BrimLines") == null) {
            properties.setProperty("BrimLines", "0");
        }
        result.setBrimLines(getIntegerProperty("BrimLines"));
        result.setShield(getBooleanProperty("Shield"));
        final File shieldStlFile = new File(reprapDirectory, SHIELD_STL_FILENAME);
        ensureCorrectStlFilePosition(shieldStlFile);
        result.setShieldStlFile(SHIELD_STL_FILENAME);
        result.setDumpX(getIntegerProperty("DumpX(mm)"));
        result.setDumpY(getIntegerProperty("DumpY(mm)"));
        if (properties.getProperty("Support") == null) {
            properties.setProperty("Support", "false");
        }
        result.setSupport(getBooleanProperty("Support"));
        final int supportExtruderNo = findExtruderWithMaterial(properties.getProperty("Extruder0_SupportMaterialType(name)"));
        result.setSupportPattern(new LinearFillPattern(getDoubleProperty("Extruder" + supportExtruderNo
                + "_EvenHatchDirection(degrees)")));
        result.setSupportSpacing(getDoubleProperty("Extruder" + supportExtruderNo + "_ExtrusionBroadWidth(mm)"));
        result.setRaftLayers(getIntegerProperty("FoundationLayers"));
        result.setVerboseGCode(getBooleanProperty("Debug"));
        // assumes physNo# corresponds to No in the resulting properties
        result.setSupportExtruder(getIntegerProperty("Extruder" + supportExtruderNo + "_Address"));
        result.setPathOptimize(getBooleanProperty("PathOptimise"));
        result.setInsideOut(getBooleanProperty("Extruder0_InsideOut"));
        result.setMiddleStart(getBooleanProperty("Extruder0_MiddleStart"));
        result.setArcCompensation(getDoubleProperty("Extruder0_ArcCompensationFactor(0..)"));
        result.setArcShortSides(getDoubleProperty("Extruder0_ArcShortSides(0..)"));
        result.setInfillOverlap(getDoubleProperty("Extruder0_InfillOverlap(mm)"));
        return result;
    }

    private boolean getBooleanProperty(final String key) {
        return Boolean.parseBoolean(properties.getProperty(key));
    }

    private double getDoubleProperty(final String key) {
        return Double.parseDouble(properties.getProperty(key));
    }

    private int getIntegerProperty(final String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    private void ensureCorrectStlFilePosition(final File shieldStlFile) {
        if (shieldStlFile.canRead()) {
            return;
        }
        final File legacyStlFile = new File(new File(reprapDirectory, machineName), SHIELD_STL_FILENAME);
        if (legacyStlFile.exists()) {
            if (!legacyStlFile.renameTo(shieldStlFile)) {
                throw new RuntimeException("File " + legacyStlFile.getAbsolutePath()
                        + " exists, but cannot be moved to its new position: " + shieldStlFile.getAbsolutePath() + ".");
            }
            return;
        }
        throw new RuntimeException("Neither " + legacyStlFile.getAbsolutePath() + " nor " + shieldStlFile.getAbsolutePath()
                + " exist.");
    }

    private int findExtruderWithMaterial(final String material) {
        final String[] names = getMaterialNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(material)) {
                return i;
            }
        }
        throw new RuntimeException("no extruder that extrudes " + material + " found");
    }

    private String[] getMaterialNames() {
        final int extruderCount = getIntegerProperty("NumberOfExtruders");
        final String[] result = new String[extruderCount];

        for (int i = 0; i < extruderCount; i++) {
            final String prefix = "Extruder" + i + "_";
            result[i] = properties.getProperty(prefix + "MaterialType(name)");
        }

        return result;
    }

    private int removeUnusedExtruders() {
        final int startMaximum = getIntegerProperty("NumberOfExtruders");
        int maxExtruder = -1;
        do {
            final int nextExtruder = nextExtruderNumber(maxExtruder, startMaximum);
            if (nextExtruder == maxExtruder) {
                break;
            }
            maxExtruder++;
            if (nextExtruder > maxExtruder) {
                shiftDownExtruderNumber(nextExtruder, maxExtruder);
            }
        } while (true);
        final int remaining = maxExtruder + 1;

        for (final Iterator<?> iterator = properties.keySet().iterator(); iterator.hasNext();) {
            final String key = (String) iterator.next();
            for (int i = remaining; i < startMaximum; i++) {
                final String prefix = "Extruder" + i + "_";
                if (key.startsWith(prefix)) {
                    iterator.remove();
                    break;
                }
            }
        }

        return remaining;
    }

    private void shiftDownExtruderNumber(final int from, final int to) {
        final String prefix = "Extruder" + from + "_";
        final Map<String, String> newValues = new HashMap<>();
        for (final Object name : properties.keySet()) {
            final String key = (String) name;
            if (key.startsWith(prefix)) {
                final String remainder = key.substring(prefix.length());
                newValues.put("Extruder" + to + "_" + remainder, properties.getProperty(key));
            }
        }
        for (final String key : newValues.keySet()) {
            properties.setProperty(key, newValues.get(key));
        }
    }

    private int nextExtruderNumber(final int from, final int maximum) {
        final int nextNumber = from + 1;
        for (int i = nextNumber; i < maximum; i++) {
            final String addressKey = "Extruder" + i + "_Address";
            if (properties.containsKey(addressKey) && nextNumber == getIntegerProperty(addressKey)) {
                return i;
            }
        }
        return from;
    }
}
