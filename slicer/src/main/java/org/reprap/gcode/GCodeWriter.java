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

import org.reprap.geometry.LayerRules;
import org.reprap.utilities.Debug;

public class GCodeWriter {
    private static final String COMMENT_CHAR = ";";

    /**
     * The root file name for output (without ".gcode" on the end)
     */
    private String opFileName;

    private String layerFileNames;

    private static final String GCODE_EXTENSION = ".gcode";

    /**
     * How does the first file name in a multiple set end?
     */
    private static final String FIRST_ENDING = "_prologue";

    /**
     * Flag for temporary files
     */
    private static final String TMP_STRING = "_TeMpOrArY_";

    private PrintStream fileOutStream = null;

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
            Debug.getInstance().gcodeDebugMessage("G-code: " + cmd + " written to file");
        }
    }

    /**
     * Writes a G-code command to the file. If debugging is on, the comment is
     * also added.
     */
    public void writeCommand(final String command, final String comment) throws IOException {
        if (Debug.getInstance().isDebug()) {
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
        if (!file.exists()) {
            Debug.getInstance().errorMessage("GCodeReaderAndWriter().copyFile: can't find file " + file.getAbsolutePath());
            return;
        }
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

            try {
                boolean doe = false;
                String fn = opFileName;
                if (topDown) {
                    fn += FIRST_ENDING;
                    fn += TMP_STRING;
                    doe = true;
                }
                fn += GCODE_EXTENSION;

                Debug.getInstance().debugMessage("opening: " + fn);
                final File fl = new File(fn);
                if (doe) {
                    fl.deleteOnExit();
                }
                final FileOutputStream fileStream = new FileOutputStream(fl);
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
            } catch (final FileNotFoundException e) {
                Debug.getInstance().errorMessage("Can't write to file '" + opFileName);
                opFileName = null;
                fileOutStream = null;
            }
        } else {
            fileOutStream = null;
        }
        return null;
    }

    public void startingLayer(final LayerRules lc) {
        lc.setLayerFileName(layerFileNames + "reprap" + lc.getMachineLayer() + TMP_STRING + GCODE_EXTENSION);
        if (!lc.getReversing()) {
            try {
                final File fl = new File(lc.getLayerFileName());
                fl.deleteOnExit();
                final FileOutputStream fileStream = new FileOutputStream(fl);
                fileOutStream = new PrintStream(fileStream);
            } catch (final Exception e) {
                Debug.getInstance().errorMessage("Can't write to file " + lc.getLayerFileName());
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
}