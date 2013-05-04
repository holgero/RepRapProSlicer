package org.reprap.configuration;

import static org.reprap.configuration.MathRoutines.circleAreaForDiameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.vecmath.Color3f;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A single centralised repository of the user's current preference settings.
 * This also implements (almost) a singleton for easy global access. If there
 * are no current preferences the system-wide ones are copied to the user's
 * space.
 */
public class Preferences {
    private static final String SHIELD_STL_FILENAME = "shield.stl";
    private static final Logger LOGGER = LogManager.getLogger(Preferences.class);
    static final String PROPERTIES_FOLDER = ".reprap";
    private static final String PROPERTIES_DIR_DISTRIBUTION = "reprap-configurations";
    private static final String PROLOGUE_FILE = "prologue.gcode";
    private static final String EPILOGUE_FILE = "epilogue.gcode";
    private static final String BASE_FILE = "base.stl";
    private static String propsFile = "reprap.properties";

    static {
        final File reprapRootDir = getReprapRootDir();
        if (!reprapRootDir.exists()) {
            copySystemConfigurations(reprapRootDir);
        }
    }
    private static final Preferences globalPrefs = new Preferences();

    private static void copySystemConfigurations(final File usersDir) {
        try {
            final URL systemConfigurationURL = getSystemConfiguration();
            switch (systemConfigurationURL.getProtocol()) {
            case "file":
                FileUtils.copyDirectory(toFile(systemConfigurationURL), usersDir);
                break;
            case "jar":
                copyJarTree(systemConfigurationURL, usersDir);
                break;
            default:
                throw new IllegalArgumentException("Cant copy resource stream from " + systemConfigurationURL);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File toFile(final URL url) {
        return new File(URI.create(url.toString()));
    }

    private static void copyJarTree(final URL source, final File target) throws IOException {
        final JarURLConnection jarConnection = (JarURLConnection) source.openConnection();
        final String prefix = jarConnection.getEntryName();
        final JarFile jarFile = jarConnection.getJarFile();
        for (final JarEntry jarEntry : Collections.list(jarFile.entries())) {
            final String entryName = jarEntry.getName();
            if (entryName.startsWith(prefix)) {
                if (!jarEntry.isDirectory()) {
                    final String fileName = StringUtils.removeStart(entryName, prefix);
                    final InputStream fileStream = jarFile.getInputStream(jarEntry);
                    try {
                        FileUtils.copyInputStreamToFile(fileStream, new File(target, fileName));
                    } finally {
                        fileStream.close();
                    }
                }
            }
        }
    }

    /**
     * Where the user stores all their configuration files
     */
    private static File getReprapRootDir() {
        return new File(FileUtils.getUserDirectory(), PROPERTIES_FOLDER);
    }

    /**
     * The name of the user's active machine configuration.
     */
    private static String getActiveMachineName() {
        return Machine.getActiveMachine();
    }

    /**
     * The directory containing all the user's configuration files for their
     * active machine
     */
    private static File getActiveMachineDir() {
        return new File(getReprapRootDir(), getActiveMachineName());
    }

    /**
     * Where are the system-wide master copies?
     */
    private static URL getSystemConfiguration() {
        final URL sysConfig = ClassLoader.getSystemResource(PROPERTIES_DIR_DISTRIBUTION);
        if (sysConfig == null) {
            throw new RuntimeException("Can't find system RepRap configurations: " + PROPERTIES_DIR_DISTRIBUTION);
        }
        return sysConfig;
    }

    private final CurrentConfiguration currentConfiguration;

    private Preferences() {
        final Properties mainPreferences = loadConfiguration(propsFile);
        final PrintSettings printSettings = createPrintSettings(mainPreferences);
        final int remaining = removeUnusedExtruders(mainPreferences);
        final PrinterSettings printerSettings = createPrinterSettings(mainPreferences);
        printerSettings.setExtruderSettings(createAllExtruderSettings(remaining, mainPreferences,
                printSettings.getLayerHeight()));
        currentConfiguration = new CurrentConfiguration(printSettings, printerSettings);
    }

    private static ExtruderSettings[] createAllExtruderSettings(final int extruderCount, final Properties properties,
            final double layerHeight) {
        final ExtruderSettings[] extruderSettings = new ExtruderSettings[extruderCount];
        for (int i = 0; i < extruderSettings.length; i++) {
            final ExtruderSettings settings = createExtruderSettings(i, properties, layerHeight);
            extruderSettings[i] = settings;
        }
        return extruderSettings;
    }

    private static ExtruderSettings createExtruderSettings(final int number, final Properties properties,
            final double layerHeight) {
        final ExtruderSettings settings = new ExtruderSettings();
        // this is wrong, but the migration from the properties must somehow guess the nozzle diameter
        final String prefix = "Extruder" + number + "_";
        settings.setNozzleDiameter(getDoubleProperty(properties, prefix + "ExtrusionSize(mm)"));
        settings.setRetraction(toFilamentLength(properties, getDoubleProperty(properties, prefix + "Reverse(ms)"), number,
                layerHeight));
        settings.setExtraLengthPerLayer(toFilamentLength(properties,
                getDoubleProperty(properties, prefix + "ExtrusionDelayForLayer(ms)"), number, layerHeight));
        settings.setExtraLengthPerPolygon(toFilamentLength(properties,
                getDoubleProperty(properties, prefix + "ExtrusionDelayForPolygon(ms)"), number, layerHeight));
        settings.setExtrudeRatio(getDoubleProperty(properties, prefix + "ExtrudeRatio(0..)"));
        settings.setEarlyRetraction(getIntegerProperty(properties, prefix + "ExtrusionOverRun(mm)"));
        settings.setAirExtrusionFeedRate(getDoubleProperty(properties, prefix + "FastEFeedrate(mm/minute)"));
        settings.setPrintExtrusionRate(getDoubleProperty(properties, prefix + "FastXYFeedrate(mm/minute)"));
        settings.setLift(getDoubleProperty(properties, prefix + "Lift(mm)"));
        settings.setMaterial(createMaterialSettings(properties, prefix));
        return settings;
    }

    private static MaterialSettings createMaterialSettings(final Properties properties, final String prefix) {
        final MaterialSettings material = new MaterialSettings();
        material.setName(properties.getProperty(prefix + "MaterialType(name)"));
        material.setDiameter(getDoubleProperty(properties, prefix + "FeedDiameter(mm)"));
        material.setColor(new Color3f(loadColorComponent(properties, prefix, "R"), loadColorComponent(properties, prefix, "G"),
                loadColorComponent(properties, prefix, "B")));
        return material;
    }

    private static float loadColorComponent(final Properties properties, final String prefix, final String component) {
        return (float) getDoubleProperty(properties, prefix + "Colour" + component + "(0..1)");
    }

    private static PrinterSettings createPrinterSettings(final Properties properties) {
        final PrinterSettings result = new PrinterSettings();
        result.setName(getActiveMachineName());
        result.setBedSizeX(getDoubleProperty(properties, "WorkingX(mm)"));
        result.setBedSizeY(getDoubleProperty(properties, "WorkingY(mm)"));
        result.setMaximumZ(getDoubleProperty(properties, "WorkingZ(mm)"));
        result.setRelativeDistanceE(getBooleanProperty(properties, "ExtrusionRelative"));
        result.setMaximumFeedrateX(getDoubleProperty(properties, "MaximumFeedrateX(mm/minute)"));
        result.setMaximumFeedrateY(getDoubleProperty(properties, "MaximumFeedrateY(mm/minute)"));
        result.setMaximumFeedrateZ(getDoubleProperty(properties, "MaximumFeedrateZ(mm/minute)"));
        result.setPrologueFile(new File(getActiveMachineDir(), PROLOGUE_FILE));
        result.setEpilogueFile(new File(getActiveMachineDir(), EPILOGUE_FILE));
        result.setBuildPlatformStl(new File(getActiveMachineDir(), BASE_FILE));
        return result;
    }

    private static PrintSettings createPrintSettings(final Properties properties) {
        final PrintSettings result = new PrintSettings();
        result.setLayerHeight(getDoubleProperty(properties, "Extruder0_ExtrusionHeight(mm)"));
        result.setVerticalShells(getIntegerProperty(properties, "Extruder0_NumberOfShells(0..N)"));
        result.setHorizontalShells(getIntegerProperty(properties, "Extruder0_SurfaceLayers(0..N)"));
        // extruder #3 is the first infill extruder in the default configuration file
        final double fillDensity = getDoubleProperty(properties, "Extruder3_ExtrusionSize(mm)")
                / getDoubleProperty(properties, "Extruder3_ExtrusionInfillWidth(mm)");
        LOGGER.info("fill density is: " + fillDensity);
        result.setFillDensity(fillDensity);
        result.setFillPattern(new RectilinearFillPattern(getDoubleProperty(properties, "Extruder0_EvenHatchDirection(degrees)")));
        result.setPerimeterSpeed(getDoubleProperty(properties, "Extruder0_OutlineSpeed(0..1)"));
        result.setInfillSpeed(getDoubleProperty(properties, "Extruder0_InfillSpeed(0..1)"));
        result.setSkirt(getBooleanProperty(properties, "StartRectangle"));
        if (properties.getProperty("BrimLines") == null) {
            properties.setProperty("BrimLines", "0");
        }
        result.setBrimLines(getIntegerProperty(properties, "BrimLines"));
        result.setShield(getBooleanProperty(properties, "Shield"));
        final File shieldStlFile = new File(getReprapRootDir(), SHIELD_STL_FILENAME);
        ensureCorrectStlFilePosition(shieldStlFile);
        result.setShieldStlFile(shieldStlFile);
        result.setDumpX(getIntegerProperty(properties, "DumpX(mm)"));
        result.setDumpY(getIntegerProperty(properties, "DumpY(mm)"));
        if (properties.getProperty("Support") == null) {
            properties.setProperty("Support", "false");
        }
        result.setSupport(getBooleanProperty(properties, "Support"));
        final int supportExtruderNo = findExtruderWithMaterial(properties,
                properties.getProperty("Extruder0_SupportMaterialType(name)"));
        result.setSupportPattern(new LinearFillPattern(getDoubleProperty(properties, "Extruder" + supportExtruderNo
                + "_EvenHatchDirection(degrees)")));
        result.setSupportSpacing(getDoubleProperty(properties, "Extruder" + supportExtruderNo + "_ExtrusionBroadWidth(mm)"));
        result.setRaftLayers(getIntegerProperty(properties, "FoundationLayers"));
        result.setVerboseGCode(getBooleanProperty(properties, "Debug"));
        // assumes physNo# corresponds to No in the resulting properties
        result.setSupportExtruder(getIntegerProperty(properties, "Extruder" + supportExtruderNo + "_Address"));
        result.setPathOptimize(getBooleanProperty(properties, "PathOptimise"));
        result.setInsideOut(getBooleanProperty(properties, "Extruder0_InsideOut"));
        result.setMiddleStart(getBooleanProperty(properties, "Extruder0_MiddleStart"));
        result.setArcCompensation(getDoubleProperty(properties, "Extruder0_ArcCompensationFactor(0..)"));
        result.setArcShortSides(getDoubleProperty(properties, "Extruder0_ArcShortSides(0..)"));
        result.setInfillOverlap(getDoubleProperty(properties, "Extruder0_InfillOverlap(mm)"));
        return result;
    }

    private static void ensureCorrectStlFilePosition(final File shieldStlFile) {
        if (shieldStlFile.canRead()) {
            return;
        }
        final File legacyStlFile = new File(getActiveMachineDir(), SHIELD_STL_FILENAME);
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

    private static int findExtruderWithMaterial(final Properties properties, final String material) {
        final String[] names = getMaterialNames(properties);
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(material)) {
                return i;
            }
        }
        throw new RuntimeException("no extruder that extrudes " + material + " found");
    }

    private static String[] getMaterialNames(final Properties properties) {
        final int extruderCount = getIntegerProperty(properties, "NumberOfExtruders");
        final String[] result = new String[extruderCount];

        for (int i = 0; i < extruderCount; i++) {
            final String prefix = "Extruder" + i + "_";
            result[i] = properties.getProperty(prefix + "MaterialType(name)");
        }

        return result;
    }

    private static boolean getBooleanProperty(final Properties properties, final String key) {
        return Boolean.parseBoolean(properties.getProperty(key));
    }

    private static double getDoubleProperty(final Properties properties, final String key) {
        return Double.parseDouble(properties.getProperty(key));
    }

    private static int getIntegerProperty(final Properties properties, final String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    private static int removeUnusedExtruders(final Properties properties) {
        final int startMaximum = getIntegerProperty(properties, "NumberOfExtruders");
        int maxExtruder = -1;
        do {
            final int nextExtruder = nextExtruderNumber(properties, maxExtruder, startMaximum);
            if (nextExtruder == maxExtruder) {
                break;
            }
            maxExtruder++;
            if (nextExtruder > maxExtruder) {
                shiftDownExtruderNumber(properties, nextExtruder, maxExtruder);
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

    private static void shiftDownExtruderNumber(final Properties properties, final int from, final int to) {
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

    private static int nextExtruderNumber(final Properties properties, final int from, final int maximum) {
        final int nextNumber = from + 1;
        for (int i = nextNumber; i < maximum; i++) {
            final String addressKey = "Extruder" + i + "_Address";
            if (properties.containsKey(addressKey) && nextNumber == getIntegerProperty(properties, addressKey)) {
                return i;
            }
        }
        return from;
    }

    private static double toFilamentLength(final Properties properties, final double delay, final int extruder,
            final double layerHeight) {
        final String prefix = "Extruder" + extruder + "_";
        final double extrusionSpeed = getDoubleProperty(properties, prefix + "ExtrusionSpeed(mm/minute)");
        final double feedDiameter = getDoubleProperty(properties, prefix + "FeedDiameter(mm)");
        final double extrusionSize = getDoubleProperty(properties, prefix + "ExtrusionSize(mm)");
        return extrusionSpeed * delay / 60000 * layerHeight * extrusionSize / circleAreaForDiameter(feedDiameter);
    }

    private static Properties load(final File mainFile) {
        final Properties mainPreferences = new Properties();
        try {
            final InputStream preferencesStream = new FileInputStream(mainFile);
            try {
                mainPreferences.load(preferencesStream);
            } finally {
                preferencesStream.close();
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        comparePreferences(mainPreferences);
        return mainPreferences;
    }

    /**
     * Compare the user's preferences with the distribution one and report any
     * different names.
     */
    private static void comparePreferences(final Properties mainPreferences) {
        final Properties sysPreferences;
        try {
            sysPreferences = loadSystemProperties();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        final Enumeration<?> usersLot = mainPreferences.propertyNames();
        final Enumeration<?> distLot = sysPreferences.propertyNames();

        String result = "";
        int count = 0;
        boolean noDifference = true;

        while (usersLot.hasMoreElements()) {
            final String next = (String) usersLot.nextElement();
            if (!sysPreferences.containsKey(next)) {
                result += " " + next + "\n";
                count++;
            }
        }
        if (count > 0) {
            result = "Your preferences file contains:\n" + result + "which ";
            if (count > 1) {
                result += "are";
            } else {
                result += "is";
            }
            result += " not in the distribution preferences file.";
            LOGGER.debug(result);
            noDifference = false;
        }

        result = "";
        count = 0;
        while (distLot.hasMoreElements()) {
            final String next = (String) distLot.nextElement();
            if (!mainPreferences.containsKey(next)) {
                result += " " + next + "\n";
                count++;
            }
        }

        if (count > 0) {
            result = "The distribution preferences file contains:\n" + result + "which ";
            if (count > 1) {
                result += "are";
            } else {
                result += "is";
            }
            result += " not in your preferences file.";
            LOGGER.debug(result);
            noDifference = false;
        }

        if (noDifference) {
            LOGGER.debug("The distribution preferences file and yours match.  This is good.");
        }
    }

    private static Properties loadSystemProperties() throws IOException {
        final String systemPropertiesPath = PROPERTIES_DIR_DISTRIBUTION + "/" + getActiveMachineName() + "/" + propsFile;
        final URL sysProperties = ClassLoader.getSystemResource(systemPropertiesPath);
        final InputStream sysPropStream = sysProperties.openStream();
        try {
            final Properties systemProperties = new Properties();
            systemProperties.load(sysPropStream);
            return systemProperties;
        } finally {
            sysPropStream.close();
        }
    }

    private static Properties loadConfiguration(final String fileName) {
        return load(new File(getActiveMachineDir(), fileName));
    }

    public static CurrentConfiguration getCurrentConfiguration() {
        return globalPrefs.currentConfiguration;
    }

    public PrintSettings getPrintSettings() {
        return currentConfiguration.getPrintSettings();
    }

    public PrinterSettings getPrinterSettings() {
        return currentConfiguration.getPrinterSettings();
    }
}
