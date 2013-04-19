package org.reprap.gcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.reprap.configuration.PreferenceChangeListener;
import org.reprap.configuration.Preferences;
import org.reprap.geometry.LayerRules;

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
    private void queue(String cmd) throws IOException {
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
    public void writeCommand(final String command, final String comment) throws IOException {
        if (debugGcode) {
            queue(command + " " + COMMENT_CHAR + " " + comment);
        } else {
            queue(command);
        }
    }

    /**
     * Writes a comment line to the file.
     */
    public void writeComment(final String comment) throws IOException {
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

    public String setGCodeFileForOutput(final boolean topDown, final String fileRoot) {
        final File defaultFile = new File(fileRoot + ".gcode");
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(defaultFile);
        FileFilter filter;
        filter = new FileNameExtensionFilter("G Code file to write to", new String[] { "gcode" });
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        opFileName = null;
        final int result = chooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            opFileName = chooser.getSelectedFile().getAbsolutePath();
            if (opFileName.endsWith(GCODE_EXTENSION)) {
                opFileName = opFileName.substring(0, opFileName.length() - 6);
            }

            boolean doe = false;
            String fn = opFileName;
            if (topDown) {
                fn += FIRST_ENDING;
                fn += TMP_STRING;
                doe = true;
            }
            fn += GCODE_EXTENSION;

            LOGGER.debug("opening: " + fn);
            final File fl = new File(fn);
            if (doe) {
                fl.deleteOnExit();
            }
            final FileOutputStream fileStream;
            try {
                fileStream = new FileOutputStream(fl);
            } catch (final FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            fileOutStream = new PrintStream(fileStream);
            String shortName = chooser.getSelectedFile().getName();
            if (!shortName.endsWith(GCODE_EXTENSION)) {
                shortName += GCODE_EXTENSION;
            }
            layerFileNames = System.getProperty("java.io.tmpdir") + File.separator + shortName;
            final File rfod = new File(layerFileNames);
            if (!rfod.mkdir()) {
                throw new RuntimeException(layerFileNames);
            }
            rfod.deleteOnExit();
            layerFileNames += File.separator;
            return shortName;
        } else {
            fileOutStream = null;
        }
        return null;
    }

    public void startingLayer(final LayerRules lc) {
        lc.setLayerFileName(layerFileNames + "reprap" + lc.getMachineLayer() + TMP_STRING + GCODE_EXTENSION);
        if (!lc.getReversing()) {
            final File fl = new File(lc.getLayerFileName());
            fl.deleteOnExit();
            try {
                final FileOutputStream fileStream = new FileOutputStream(fl);
                fileOutStream = new PrintStream(fileStream);
            } catch (final FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void finishedLayer(final LayerRules lc) {
        if (!lc.getReversing()) {
            fileOutStream.close();
        }
    }

    public String getOutputFilename() {
        return opFileName + GCODE_EXTENSION;
    }

    @Override
    public void refreshPreferences(final Preferences preferences) {
        debugGcode = preferences.loadBool("Debug");
    }
}