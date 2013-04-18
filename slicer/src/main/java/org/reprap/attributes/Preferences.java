package org.reprap.attributes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.reprap.utilities.Debug;

/**
 * A single centralised repository of the user's current preference settings.
 * This also implements (almost) a singleton for easy global access. If there
 * are no current preferences the system-wide ones are copied to the user's
 * space.
 */
public class Preferences {
    static final String PROPERTIES_FOLDER = ".reprap";
    private static final String PROPERTIES_DIR_DISTRIBUTION = "reprap-configurations";
    private static final String PROLOGUE_FILE = "prologue.gcode";
    private static final String EPILOGUE_FILE = "epilogue.gcode";
    private static final String BASE_FILE = "base.stl";
    private static final char ACTIVE_FLAG = '*';
    private static final int GRID_SIZE = 100;
    private static final double GRID_RESOLUTION = 1.0 / GRID_SIZE;
    private static final double TINY = 1.0e-12; // A small number
    private static final double MACHINE_RESOLUTION = 0.05; // RepRap step size in mm
    private static final double INCH_TO_MM = 25.4;

    private static String propsFile = "reprap.properties";

    static {
        final File reprapRootDir = getReprapRootDir();
        if (!reprapRootDir.exists()) {
            copySystemConfigurations(reprapRootDir);
        }
    }
    private static final Preferences globalPrefs = new Preferences();
    static {
        // first thing (after we have set globalPrefs): apply the loaded debug preferences
        Debug.refreshPreferences(globalPrefs.loadBool("Debug"), false);
        globalPrefs.comparePreferences();
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
        for (final Machine machine : Machine.getAllMachines()) {
            if (machine.isActive()) {
                return machine.getName();
            }
        }
        Debug.getInstance().errorMessage(
                "No active RepRap set (add " + ACTIVE_FLAG + " to the start of a line in the file: " + Machine.getMachineFile()
                        + ").");
        return "";
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
            Debug.getInstance().errorMessage("Can't find system RepRap configurations: " + PROPERTIES_DIR_DISTRIBUTION);
        }
        return sysConfig;
    }

    public static String getDefaultPropsFile() {
        return propsFile;
    }

    private final Properties mainPreferences = new Properties();
    private final Set<PreferenceChangeListener> listeners = new HashSet<>();

    private Preferences() {
        loadConfiguration(propsFile);
    }

    public double gridResultion() {
        return GRID_RESOLUTION;
    }

    public double tinyValue() {
        return TINY;
    }

    public double getMachineResolution() {
        return MACHINE_RESOLUTION;
    }

    public double inchesToMillimeters() {
        return INCH_TO_MM;
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
            Debug.getInstance().debugMessage(result);
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
            Debug.getInstance().debugMessage(result);
            noDifference = false;
        }

        if (noDifference) {
            Debug.getInstance().debugMessage("The distribution preferences file and yours match.  This is good.");
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

    public void save() throws FileNotFoundException, IOException {
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

    private void notifyPreferenceChangeListeners() throws IOException {
        for (final PreferenceChangeListener listener : listeners) {
            listener.refreshPreferences(this);
        }
        Debug.refreshPreferences(getInstance().loadBool("Debug"), false);
    }

    public String loadString(final String name) {
        if (!mainPreferences.containsKey(name)) {
            Debug.getInstance().errorMessage(
                    "RepRap preference: " + name + " not found in your preference file: " + getActiveMachineDir() + propsFile);
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

    /**
     * @return an array of all the names of all the materials in extruders
     */
    public static String[] allMaterials() throws IOException {
        final int extruderCount = globalPrefs.loadInt("NumberOfExtruders");
        final String[] result = new String[extruderCount];

        for (int i = 0; i < extruderCount; i++) {
            final String prefix = "Extruder" + i + "_";
            result[i] = globalPrefs.loadString(prefix + "MaterialType(name)");
        }

        return result;
    }

    public static String[] startsWith(final String prefix) throws IOException {
        final Enumeration<?> allOfThem = globalPrefs.mainPreferences.propertyNames();
        final List<String> r = new ArrayList<String>();

        while (allOfThem.hasMoreElements()) {
            final String next = (String) allOfThem.nextElement();
            if (next.startsWith(prefix)) {
                r.add(next);
            }
        }
        final String[] result = new String[r.size()];

        for (int i = 0; i < r.size(); i++) {
            result[i] = r.get(i);
        }

        return result;
    }

    public static String[] notStartsWith(final String prefix) throws IOException {
        final Enumeration<?> allOfThem = globalPrefs.mainPreferences.propertyNames();
        final List<String> r = new ArrayList<String>();

        while (allOfThem.hasMoreElements()) {
            final String next = (String) allOfThem.nextElement();
            if (!next.startsWith(prefix)) {
                r.add(next);
            }
        }

        final String[] result = new String[r.size()];

        for (int i = 0; i < r.size(); i++) {
            result[i] = r.get(i);
        }

        return result;
    }

    public static Preferences getInstance() {
        return globalPrefs;
    }
}
