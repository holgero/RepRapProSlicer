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
            "Extruder\\d_SupportMaterialType\\(name\\)", "SlowXYFeedrate\\(mm/minute\\)", "SlowZFeedrate\\(mm/minute\\)",
            "InterLayerCooling", "StartRectangle", "BrimLines", "Shield", "Support", "FoundationLayers", "Debug");

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
    private final PrintSettings printSettings = new PrintSettings();

    private Preferences() {
        loadConfiguration(propsFile);
        printSettings.setLayerHeight(loadDouble("Extruder0_ExtrusionHeight(mm)"));
        printSettings.setVerticalShells(loadInt("Extruder0_NumberOfShells(0..N)"));
        printSettings.setHorizontalShells(loadInt("Extruder0_SurfaceLayers(0..N)"));
        // extruder #3 is the first infill extruder in the default configuration file
        final double fillDensity = loadDouble("Extruder3_ExtrusionSize(mm)") / loadDouble("Extruder3_ExtrusionInfillWidth(mm)");
        LOGGER.info("fill density is: " + fillDensity);
        printSettings.setFillDensity(fillDensity);
        printSettings.setFillPattern(new RectilinearFillPattern(loadDouble("Extruder0_EvenHatchDirection(degrees)")));
        printSettings.setPerimeterSpeed(loadDouble("Extruder0_OutlineSpeed(0..1)"));
        printSettings.setInfillSpeed(loadDouble("Extruder0_InfillSpeed(0..1)"));
        printSettings.setSkirt(loadBool("StartRectangle"));
        if (mainPreferences.getProperty("BrimLines") == null) {
            mainPreferences.setProperty("BrimLines", "0");
        }
        printSettings.setBrimLines(loadInt("BrimLines"));
        printSettings.setShield(loadBool("Shield"));
        if (mainPreferences.getProperty("Support") == null) {
            mainPreferences.setProperty("Support", "false");
        }
        printSettings.setSupport(loadBool("Support"));
        printSettings.setRaftLayers(loadInt("FoundationLayers"));
        printSettings.setVerboseGCode(loadBool("Debug"));
        final int supportExtruderNo = getNumberFromMaterial(loadString("Extruder0_SupportMaterialType(name)"));
        printSettings.setSupportExtruder(supportExtruderNo);
        fixupExtruderDelayProperties();
        removeUnusedProperties();
    }

    private void fixupExtruderDelayProperties() {
        final Map<String, String> newValues = new HashMap<>();
        calculateDistance(newValues, "Reverse\\(ms\\)", "RetractionDistance(mm)");
        calculateDistance(newValues, "ExtrusionDelayForLayer\\(ms\\)", "ExtraExtrusionDistanceForLayer(mm)");
        calculateDistance(newValues, "ExtrusionDelayForPolygon\\(ms\\)", "ExtraExtrusionDistanceForPolygon(mm)");
        for (final String key : newValues.keySet()) {
            mainPreferences.setProperty(key, newValues.get(key));
        }
    }

    private void calculateDistance(final Map<String, String> newValues, final String delayName, final String distanceName) {
        final Pattern pattern = Pattern.compile("Extruder(\\d)_" + delayName);
        for (final Object name : mainPreferences.keySet()) {
            final String key = (String) name;
            final Matcher matcher = pattern.matcher(key);
            if (matcher.matches()) {
                final int extruderNo = Integer.parseInt(matcher.group(1));
                newValues.put("Extruder" + extruderNo + "_" + distanceName,
                        Double.toString(toFilamentLength(loadDouble(key), extruderNo)));
            }
        }
    }

    private double toFilamentLength(final double delay, final int extruder) {
        final String prefix = "Extruder" + extruder + "_";
        final double extrusionSpeed = loadDouble(prefix + "ExtrusionSpeed(mm/minute)");
        final double feedDiameter = loadDouble(prefix + "FeedDiameter(mm)");
        final double extrusionSize = loadDouble(prefix + "ExtrusionSize(mm)");
        return extrusionSpeed * delay / 60000 * printSettings.getLayerHeight() * extrusionSize
                / (feedDiameter * feedDiameter * Math.PI / 4);
    }

    private void removeUnusedProperties() {
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
        LOGGER.debug("getNumberFromMaterial - can't find " + material);
        return -1;
    }

    /**
     * @return an array of all the names of all the materials in extruders
     */
    public String[] getAllMaterials() {
        final int extruderCount = loadInt("NumberOfExtruders");
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
}
