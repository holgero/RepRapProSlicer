package org.reprap.attributes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.media.j3d.Appearance;
import javax.media.j3d.Material;
import javax.vecmath.Color3f;

import org.reprap.Main;
import org.reprap.utilities.Debug;
import org.reprap.utilities.RepRapUtils;

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
    private static final Color3f BLACK = new Color3f(0, 0, 0);

    private static String propsFile = "reprap.properties";
    private static Preferences globalPrefs = null;
    private static boolean displaySimulation = false;

    private final Properties mainPreferences = new Properties();

    public static double gridRes() {
        return GRID_RESOLUTION;
    }

    public static double tiny() {
        return TINY;
    }

    public static double machineResolution() {
        return MACHINE_RESOLUTION;
    }

    public static double inchesToMillimetres() {
        return INCH_TO_MM;
    }

    public static boolean simulate() {
        return displaySimulation;
    }

    public static void setSimulate(final boolean s) {
        displaySimulation = s;
    }

    private Preferences() throws IOException {
        final File mainDir = new File(getUsersRootDir());
        if (!mainDir.exists()) {
            copySystemConfigurations(mainDir);
        }

        // Construct URL of user properties file
        final String path = getPropertiesPath();
        final File mainFile = new File(path);
        final URL mainUrl = mainFile.toURI().toURL();

        if (mainFile.exists()) {
            final InputStream preferencesStream = mainUrl.openStream();
            try {
                mainPreferences.load(preferencesStream);
            } finally {
                preferencesStream.close();
            }
        } else {
            Debug.getInstance().errorMessage("Can't find your RepRap configurations: " + getPropertiesPath());
        }
    }

    private static void copySystemConfigurations(final File usersDir) throws IOException {
        final URL sysConfig = getSystemConfiguration();
        RepRapUtils.copyResourceTree(sysConfig, usersDir);
    }

    /**
     * Where the user stores all their configuration files
     */
    public static String getUsersRootDir() {
        return System.getProperty("user.home") + File.separatorChar + PROPERTIES_FOLDER + File.separatorChar;
    }

    /**
     * The name of the user's active machine configuration (without the leading
     * *)
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
    public static String getActiveMachineDir() {
        return getUsersRootDir() + getActiveMachineName() + File.separatorChar;
    }

    /**
     * Where the user's properties file is
     */
    private static String getPropertiesPath() {
        return getActiveMachineDir() + propsFile;
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

    /**
     * Where the user's build-base STL file is
     */
    public static String getBasePath() {
        return getActiveMachineDir() + BASE_FILE;
    }

    /**
     * Where the user's GCode prologue file is
     */
    public static String getProloguePath() {
        return getActiveMachineDir() + PROLOGUE_FILE;
    }

    /**
     * Where the user's GCode epilogue file is
     */
    public static String getEpiloguePath() {
        return getActiveMachineDir() + EPILOGUE_FILE;
    }

    /**
     * Compare the user's preferences with the distribution one and report any
     * different names.
     */
    private void comparePreferences() throws IOException {
        final Properties sysPreferences = loadSystemProperties();

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
        final Properties systemProperties = new Properties();
        final String systemPropertiesPath = PROPERTIES_DIR_DISTRIBUTION + "/" + getActiveMachineName() + "/" + propsFile;
        final URL sysProperties = ClassLoader.getSystemResource(systemPropertiesPath);
        if (sysProperties != null) {
            final InputStream sysPropStream = sysProperties.openStream();
            try {
                systemProperties.load(sysPropStream);
            } finally {
                sysPropStream.close();
            }
        } else {
            Debug.getInstance().errorMessage("Can't find system properties: " + systemPropertiesPath);
        }
        return systemProperties;
    }

    private void save() throws FileNotFoundException, IOException {
        final String savePath = getPropertiesPath();
        final File f = new File(savePath);
        if (!f.exists()) {
            f.createNewFile();
        }

        final OutputStream output = new FileOutputStream(f);
        mainPreferences.store(output, "RepRap machine parameters. See http://reprap.org/wiki/Java_Software_Preferences_File");

        notifyPreferenceChangeListeners();
    }

    private void notifyPreferenceChangeListeners() throws IOException {
        Main.gui.getPrinter().refreshPreferences();
        Debug.refreshPreferences(loadGlobalBool("Debug"), false);
    }

    private String loadString(final String name) {
        if (mainPreferences.containsKey(name)) {
            return mainPreferences.getProperty(name);
        }
        Debug.getInstance().errorMessage(
                "RepRap preference: " + name + " not found in your preference file: " + getPropertiesPath());
        return null;
    }

    private int loadInt(final String name) {
        final String strVal = loadString(name);
        return Integer.parseInt(strVal);
    }

    private double loadDouble(final String name) {
        final String strVal = loadString(name);
        return Double.parseDouble(strVal);
    }

    private boolean loadBool(final String name) {
        final String strVal = loadString(name);
        if (strVal == null) {
            return false;
        }
        if (strVal.length() == 0) {
            return false;
        }
        if (strVal.compareToIgnoreCase("true") == 0) {
            return true;
        }
        return false;
    }

    public static synchronized boolean loadConfig(final String configName) {
        propsFile = configName;

        try {
            globalPrefs = new Preferences();
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    synchronized private static void initIfNeeded() throws IOException {
        if (globalPrefs == null) {
            globalPrefs = new Preferences();

            // first thing (after we have set globalPrefs): apply the loaded debug preferences
            Debug.refreshPreferences(loadGlobalBool("Debug"), false);
            globalPrefs.comparePreferences();
        }
    }

    public static String loadGlobalString(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadString(name);
    }

    public static int loadGlobalInt(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadInt(name);
    }

    public static double loadGlobalDouble(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadDouble(name);
    }

    public static boolean loadGlobalBool(final String name) throws IOException {
        initIfNeeded();
        return globalPrefs.loadBool(name);
    }

    public static synchronized void saveGlobal() throws IOException {
        initIfNeeded();
        globalPrefs.save();
    }

    public static String getDefaultPropsFile() {
        return propsFile;
    }

    public static void setGlobalString(final String name, final String value) throws IOException {
        initIfNeeded();
        globalPrefs.setString(name, value);
    }

    private void setString(final String name, final String value) {
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
        initIfNeeded();
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
        initIfNeeded();
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

    public static Appearance unselectedApp() {
        Color3f unselectedColour = null;
        try {
            unselectedColour = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        final Appearance unselectedApp = new Appearance();
        unselectedApp.setMaterial(new Material(unselectedColour, BLACK, unselectedColour, BLACK, 0f));
        return unselectedApp;
    }
}