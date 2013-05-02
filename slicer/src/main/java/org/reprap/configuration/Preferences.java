package org.reprap.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Logger LOGGER = LogManager.getLogger(Preferences.class);
    static final String PROPERTIES_FOLDER = ".reprap";
    private static final String PROPERTIES_DIR_DISTRIBUTION = "reprap-configurations";
    private static final String PROLOGUE_FILE = "prologue.gcode";
    private static final String EPILOGUE_FILE = "epilogue.gcode";
    private static final String BASE_FILE = "base.stl";
    private static final int GRID_SIZE = 100;
    private static final double GRID_RESOLUTION = 1.0 / GRID_SIZE;
    private static final double MACHINE_RESOLUTION = 0.05; // RepRap step size in mm
    private static final List<Pattern> OBSOLETE_PROPERTIES_PATTERNS = compilePatterns("RepRapAccelerations", "FiveD",
            "Extruder\\d_ExtrusionHeight\\(mm\\)", "Extruder\\d_NumberOfShells\\(0\\.\\.N\\)",
            "Extruder\\d_SurfaceLayers\\(0\\.\\.N\\)", "Extruder\\d_ExtrusionInfillWidth\\(mm\\)",
            "Extruder\\d_InFillMaterialType\\(name\\)", "Extruder\\d_EvenHatchDirection\\(degrees\\)",
            "Extruder\\d_OddHatchDirection\\(degrees\\)", "Extruder\\d_ValveDelayForLayer\\(ms\\)",
            "Extruder\\d_ValveDelayForPolygon\\(ms\\)", "Extruder\\d_ValveOverRun\\(mm\\)",
            "Extruder\\d_ValvePulseTime\\(ms\\)", "Extruder\\d_CoolingPeriod\\(s\\)", "Extruder\\d_OutlineSpeed\\(0\\.\\.1\\)",
            "Extruder\\d_InfillSpeed\\(0\\.\\.1\\)", "Extruder\\d_Reverse\\(ms\\)",
            "Extruder\\d_ExtrusionDelayForLayer\\(ms\\)", "Extruder\\d_ExtrusionDelayForPolygon\\(ms\\)",
            "Extruder\\d_ExtrusionSpeed\\(mm/minute\\)", "Extruder\\d_SlowXYFeedrate\\(mm/minute\\)",
            "Extruder\\d_SupportMaterialType\\(name\\)", "Extruder\\d_Address",
            "Extruder\\d_MaxAcceleration\\(mm/minute/minute\\)", "SlowXYFeedrate\\(mm/minute\\)",
            "SlowZFeedrate\\(mm/minute\\)", "InterLayerCooling", "StartRectangle", "BrimLines", "Shield", "DumpX\\(mm\\)",
            "DumpY\\(mm\\)", "Support", "FoundationLayers", "Debug", "WorkingX\\(mm\\)", "WorkingY\\(mm\\)",
            "WorkingZ\\(mm\\)", "ExtrusionRelative", "PathOptimise", "MaximumFeedrateX\\(mm/minute\\)",
            "MaximumFeedrateY\\(mm/minute\\)", "MaximumFeedrateZ\\(mm/minute\\)", "MaxXYAcceleration\\(mm/mininute/minute\\)",
            "MaxZAcceleration\\(mm/mininute/minute\\)", "NumberOfExtruders");

    private static String propsFile = "reprap.properties";

    static {
        final File reprapRootDir = getReprapRootDir();
        if (!reprapRootDir.exists()) {
            copySystemConfigurations(reprapRootDir);
        }
    }
    private static final Preferences globalPrefs = new Preferences();

    private static List<Pattern> compilePatterns(final String... patternStrings) {
        final List<Pattern> result = new ArrayList<>();
        for (final String patternText : patternStrings) {
            result.add(Pattern.compile(patternText));
        }
        return result;
    }

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
    public static File getReprapRootDir() {
        return new File(FileUtils.getUserDirectory(), PROPERTIES_FOLDER);
    }

    /**
     * The name of the user's active machine configuration.
     */
    public static String getActiveMachineName() {
        return Machine.getActiveMachine();
    }

    /**
     * The directory containing all the user's configuration files for their
     * active machine
     */
    public static File getActiveMachineDir() {
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

    public static String getDefaultPropsFile() {
        return propsFile;
    }

    private final Properties mainPreferences = new Properties();
    private final Set<PreferenceChangeListener> listeners = new HashSet<>();
    private final PrintSettings printSettings;
    private final PrinterSettings printerSettings;

    private Preferences() {
        loadConfiguration(propsFile);
        printSettings = createPrintSettings(mainPreferences);
        final int remaining = removeUnusedExtruders(mainPreferences);
        printerSettings = createPrinterSettings(mainPreferences, remaining);
        fixupExtruderDelayProperties(mainPreferences, printSettings.getLayerHeight());
        removeUnusedProperties(mainPreferences);
    }

    private static PrinterSettings createPrinterSettings(final Properties properties, final int extruderCount) {
        final PrinterSettings result = new PrinterSettings();
        result.setBedSizeX(getDoubleProperty(properties, "WorkingX(mm)"));
        result.setBedSizeY(getDoubleProperty(properties, "WorkingY(mm)"));
        result.setMaximumZ(getDoubleProperty(properties, "WorkingZ(mm)"));
        result.setRelativeDistanceE(getBooleanProperty(properties, "ExtrusionRelative"));
        result.setMaximumFeedrateX(getDoubleProperty(properties, "MaximumFeedrateX(mm/minute)"));
        result.setMaximumFeedrateY(getDoubleProperty(properties, "MaximumFeedrateY(mm/minute)"));
        result.setMaximumFeedrateZ(getDoubleProperty(properties, "MaximumFeedrateZ(mm/minute)"));
        result.setExtruderSettings(new ExtruderSettings[extruderCount]);
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
        return result;
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

    private static void fixupExtruderDelayProperties(final Properties properties, final double layerHeight) {
        final Map<String, String> newValues = new HashMap<>();
        calculateDistance(properties, newValues, "Reverse\\(ms\\)", "RetractionDistance(mm)", layerHeight);
        calculateDistance(properties, newValues, "ExtrusionDelayForLayer\\(ms\\)", "ExtraExtrusionDistanceForLayer(mm)",
                layerHeight);
        calculateDistance(properties, newValues, "ExtrusionDelayForPolygon\\(ms\\)", "ExtraExtrusionDistanceForPolygon(mm)",
                layerHeight);
        for (final String key : newValues.keySet()) {
            properties.setProperty(key, newValues.get(key));
        }
    }

    private static void calculateDistance(final Properties properties, final Map<String, String> newValues,
            final String delayName, final String distanceName, final double layerHeight) {
        final Pattern pattern = Pattern.compile("Extruder(\\d)_" + delayName);
        for (final Object name : properties.keySet()) {
            final String key = (String) name;
            final Matcher matcher = pattern.matcher(key);
            if (matcher.matches()) {
                final int extruderNo = Integer.parseInt(matcher.group(1));
                newValues.put("Extruder" + extruderNo + "_" + distanceName, Double.toString(toFilamentLength(properties,
                        getDoubleProperty(properties, key), extruderNo, layerHeight)));
            }
        }
    }

    private static double toFilamentLength(final Properties properties, final double delay, final int extruder,
            final double layerHeight) {
        final String prefix = "Extruder" + extruder + "_";
        final double extrusionSpeed = getDoubleProperty(properties, prefix + "ExtrusionSpeed(mm/minute)");
        final double feedDiameter = getDoubleProperty(properties, prefix + "FeedDiameter(mm)");
        final double extrusionSize = getDoubleProperty(properties, prefix + "ExtrusionSize(mm)");
        return extrusionSpeed * delay / 60000 * layerHeight * extrusionSize / (feedDiameter * feedDiameter * Math.PI / 4);
    }

    private static void removeUnusedProperties(final Properties mainPreferences) {
        for (final Iterator<?> iterator = mainPreferences.keySet().iterator(); iterator.hasNext();) {
            final String key = (String) iterator.next();
            for (final Pattern pattern : OBSOLETE_PROPERTIES_PATTERNS) {
                if (pattern.matcher(key).matches()) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    public double gridResultion() {
        return GRID_RESOLUTION;
    }

    public double getMachineResolution() {
        return MACHINE_RESOLUTION;
    }

    private void load(final File mainFile) {
        mainPreferences.clear();
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
        comparePreferences();
    }

    public File getBuildBaseStlFile() {
        return new File(getActiveMachineDir(), BASE_FILE);
    }

    /**
     * Where the user's GCode prologue file is
     */
    public File getPrologueFile() {
        return new File(getActiveMachineDir(), PROLOGUE_FILE);
    }

    /**
     * Where the user's GCode epilogue file is
     */
    public File getEpilogueFile() {
        return new File(getActiveMachineDir(), EPILOGUE_FILE);
    }

    /**
     * Compare the user's preferences with the distribution one and report any
     * different names.
     */
    private void comparePreferences() {
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

    private Properties loadSystemProperties() throws IOException {
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

    public void save() throws IOException {
        final OutputStream output = new FileOutputStream(new File(getActiveMachineDir(), propsFile));
        try {
            mainPreferences.store(output,
                    "RepRap machine parameters. See http://reprap.org/wiki/Java_Software_Preferences_File");
        } finally {
            output.close();
        }
        notifyPreferenceChangeListeners();
    }

    public void registerPreferenceChangeListener(final PreferenceChangeListener listener) {
        listeners.add(listener);
    }

    private void notifyPreferenceChangeListeners() {
        for (final PreferenceChangeListener listener : listeners) {
            listener.refreshPreferences(this);
        }
    }

    public String loadString(final String name) {
        if (!mainPreferences.containsKey(name)) {
            throw new RuntimeException("RepRap preference: " + name + " not found in your preference file: "
                    + getActiveMachineDir() + "/" + propsFile);
        }
        return mainPreferences.getProperty(name);
    }

    public int loadInt(final String name) {
        return Integer.parseInt(loadString(name));
    }

    public double loadDouble(final String name) {
        return Double.parseDouble(loadString(name));
    }

    public boolean loadBool(final String name) {
        return "true".equalsIgnoreCase(loadString(name));
    }

    public void loadConfiguration(final String fileName) {
        load(new File(getActiveMachineDir(), fileName));
    }

    public void setString(final String name, final String value) {
        mainPreferences.setProperty(name, value);
    }

    private int getNumberFromMaterial(final String material) {
        if (material.equalsIgnoreCase("null")) {
            return -1;
        }

        final String[] names = getAllMaterials();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(material)) {
                return i;
            }
        }
        throw new RuntimeException("getNumberFromMaterial - can't find " + material);
    }

    /**
     * @return an array of all the names of all the materials in extruders
     */
    public String[] getAllMaterials() {
        final int extruderCount = printerSettings.getExtruderSettings().length;
        final String[] result = new String[extruderCount];

        for (int i = 0; i < extruderCount; i++) {
            final String prefix = "Extruder" + i + "_";
            result[i] = loadString(prefix + "MaterialType(name)");
        }

        return result;
    }

    public Color3f loadMaterialColor(final String material) {
        final String prefix = "Extruder" + getNumberFromMaterial(material) + "_";
        return new Color3f(loadColorComponent(prefix, "R"), loadColorComponent(prefix, "G"), loadColorComponent(prefix, "B"));
    }

    private float loadColorComponent(final String prefix, final String component) {
        return (float) loadDouble(prefix + "Colour" + component + "(0..1)");
    }

    public static Preferences getInstance() {
        return globalPrefs;
    }

    public PrintSettings getPrintSettings() {
        return printSettings;
    }

    public PrinterSettings getPrinterSettings() {
        return printerSettings;
    }
}
