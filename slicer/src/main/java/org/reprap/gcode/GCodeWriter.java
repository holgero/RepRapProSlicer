package org.reprap.gcode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class GCodeWriter {
    private static final Logger LOGGER = LogManager.getLogger(GCodeWriter.class);
    private static final Marker GCODE_MARKER = MarkerManager.getMarker("GCODE");
    private static final String COMMENT_CHAR = ";";

    private final boolean debugGcode;
    private PrintStream fileOutStream = null;

    public GCodeWriter(final boolean debugGcode) {
        this.debugGcode = debugGcode;
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
     * Writes a block of text unchanged to the file. If debugging is on, the
     * terminating comments are also added before and after the block.
     */
    public void writeBlock(final String command, final String commentBefore, final String commentAfter) {
        if (debugGcode) {
            queue(COMMENT_CHAR + " " + commentBefore);
        }
        queue(command);
        if (debugGcode) {
            queue(COMMENT_CHAR + " " + commentAfter);
        }
    }

    /**
     * Writes a comment line to the file.
     */
    public void writeComment(final String comment) {
        queue(COMMENT_CHAR + comment);
    }

    public void setGCodeFileForOutput(final File gcodeFile) {
        try {
            fileOutStream = new PrintStream(new FileOutputStream(gcodeFile));
        } catch (final FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    void closeOutFile() {
        fileOutStream.close();
    }
}