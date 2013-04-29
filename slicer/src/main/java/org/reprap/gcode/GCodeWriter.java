package org.reprap.gcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.reprap.configuration.PreferenceChangeListener;
import org.reprap.configuration.Preferences;

public class GCodeWriter implements PreferenceChangeListener {
    private static final Logger LOGGER = LogManager.getLogger(GCodeWriter.class);
    private static final Marker GCODE_MARKER = MarkerManager.getMarker("GCODE");
    private static final String COMMENT_CHAR = ";";
    private static final String GCODE_EXTENSION = ".gcode";
    /**
     * How does the first file name in a multiple set end?
     */
    private static final String FIRST_ENDING = "_prologue";
    /**
     * Flag for temporary files
     */

    /**
     * The root file name for output (without ".gcode" on the end)
     */
    private String opFileName;
    private String layerFileNames;
    private boolean debugGcode;

    private static final String TMP_STRING = "_TeMpOrArY_";

    private PrintStream fileOutStream = null;

    public GCodeWriter() {
        final Preferences preferences = Preferences.getInstance();
        preferences.registerPreferenceChangeListener(this);
        refreshPreferences(preferences);
    }

    /**
     * Force the output stream - use with caution
     */
    public void forceOutputFile(final PrintStream fos) {
        fileOutStream = fos;
    }

    /**
     * Writes a G-code command to the file.
     */
    private void queue(String cmd) {
        cmd = cmd.trim();
        cmd = cmd.replaceAll("  ", " ");

        if (fileOutStream != null) {
            fileOutStream.println(cmd);
            LOGGER.debug(GCODE_MARKER, "G-code: {} written to file", cmd);
        }
    }

    /**
     * Writes a G-code command to the file. If debugging is on, the comment is
     * also added.
     */
    public void writeCommand(final String command, final String comment) {
        if (debugGcode) {
            queue(command + " " + COMMENT_CHAR + " " + comment);
        } else {
            queue(command);
        }
    }

    /**
     * Writes a comment line to the file.
     */
    public void writeComment(final String comment) {
        queue(COMMENT_CHAR + comment);
    }

    /**
     * Copy a file of G Codes straight to output - generally used for canned
     * cycles
     * 
     * @throws IOException
     */
    public void copyFile(final File file) throws IOException {
        final FileReader fr = new FileReader(file);
        final BufferedReader br = new BufferedReader(fr);
        String s;
        while ((s = br.readLine()) != null) {
            queue(s);
        }
        fr.close();
    }

    public void setGCodeFileForOutput(final File gcodeFile) throws FileNotFoundException {
        opFileName = gcodeFile.getAbsolutePath();
        if (opFileName.endsWith(GCODE_EXTENSION)) {
            opFileName = opFileName.substring(0, opFileName.length() - 6);
        }

        final String fn = opFileName + FIRST_ENDING + TMP_STRING + GCODE_EXTENSION;
        LOGGER.debug("opening: " + fn);
        final File fl = new File(fn);
        fl.deleteOnExit();
        final FileOutputStream fileStream = new FileOutputStream(fl);
        fileOutStream = new PrintStream(fileStream);
        String shortName = gcodeFile.getName();
        if (!shortName.endsWith(GCODE_EXTENSION)) {
            shortName += GCODE_EXTENSION;
        }
        layerFileNames = System.getProperty("java.io.tmpdir") + File.separator + shortName;
        final File rfod = new File(layerFileNames);
        if (!rfod.isDirectory() && !rfod.mkdir()) {
            throw new RuntimeException("Failed to create " + layerFileNames);
        }
        rfod.deleteOnExit();
        layerFileNames += File.separator;
    }

    private File getTemporaryFile(final int layerNumber) {
        return new File(layerFileNames + "reprap" + layerNumber + TMP_STRING + GCODE_EXTENSION);
    }

    File openTemporaryOutFile(final int layerNumber) {
        final File tempFile = getTemporaryFile(layerNumber);
        try {
            final FileOutputStream fileStream = new FileOutputStream(tempFile);
            fileOutStream = new PrintStream(fileStream);
        } catch (final FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        tempFile.deleteOnExit();
        return tempFile;
    }

    void closeOutFile() {
        fileOutStream.close();
    }

    public String getOutputFilename() {
        return opFileName + GCODE_EXTENSION;
    }

    @Override
    public void refreshPreferences(final Preferences preferences) {
        debugGcode = preferences.loadBool("Debug");
    }
}