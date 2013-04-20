package org.reprap;

import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Preferences;
import org.reprap.gcode.GCodeExtruder;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.Producer;
import org.reprap.gui.MainFrame;
import org.reprap.gui.RepRapBuild;
import org.reprap.gui.SlicerFrame;

/**
 * Main RepRapProSlicer software overview. Please see http://reprap.org/ for
 * more details.
 */
public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static Main gui;

    private Producer producer = null;
    private final GCodePrinter printer;
    private final JFileChooser chooser = new JFileChooser();
    private MainFrame mainFrame;
    private SlicerFrame slicerFrame;

    public Main() throws IOException {
        printer = new GCodePrinter();
        Preferences.getInstance().registerPreferenceChangeListener(printer);
    }

    private void createAndShowGUI() throws IOException {
        JFrame.setDefaultLookAndFeelDecorated(false);
        // Required so menus float over Java3D
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        mainFrame = new MainFrame();
        slicerFrame = new SlicerFrame(mainFrame);
    }

    private GCodePrinter getPrinter() {
        return printer;
    }

    public int getLayers() {
        if (producer == null) {
            return 0;
        }
        return producer.getLayers();
    }

    public int getLayer() {
        if (producer == null) {
            return 0;
        }
        return producer.getLayer();
    }

    public boolean slice(final String gcodeFileName) {
        if (printer.setGCodeFileForOutput(gcodeFileName) == null) {
            return false;
        }
        mainFrame.producing(true);
        final Thread t = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Producer");
                try {
                    final RepRapBuild builder = mainFrame.getBuilder();
                    builder.mouseToWorld();
                    producer = new Producer(printer, builder.getSTLs(), slicerFrame);
                    printer.setLayerPause(mainFrame.getLayerPause());
                    producer.produce();
                    producer = null;
                    mainFrame.producing(false);
                    JOptionPane.showMessageDialog(mainFrame, "Slicing complete - Exit");
                    System.exit(0);
                } catch (final Exception ex) {
                    JOptionPane.showMessageDialog(mainFrame, "Production exception: " + ex);
                    ex.printStackTrace();
                }
            }
        };
        t.start();
        return true;
    }

    public File onOpen(final String description, final String[] extensions, final String defaultRoot) {
        File result;
        final FileFilter filter = new FileNameExtensionFilter(description, extensions);

        chooser.setFileFilter(filter);
        if (!defaultRoot.contentEquals("") && extensions.length == 1) {
            final File defaultFile = new File(defaultRoot + "." + extensions[0]);
            chooser.setSelectedFile(defaultFile);
        }

        final int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            result = chooser.getSelectedFile();
            if (extensions[0].toUpperCase().contentEquals("RFO")) {
                mainFrame.getBuilder().addRFOFile(result);
            }
            if (extensions[0].toUpperCase().contentEquals("STL")) {
                mainFrame.getBuilder().anotherSTLFile(result, true);
            }
            return result;
        }
        return null;
    }

    public String saveRFO(final String fileRoot) throws IOException {
        String result = null;
        File f;
        FileFilter filter;

        final File defaultFile = new File(fileRoot + ".rfo");
        final JFileChooser rfoChooser = new JFileChooser();
        rfoChooser.setSelectedFile(defaultFile);
        filter = new FileNameExtensionFilter("RFO file to write to", "rfo");
        rfoChooser.setFileFilter(filter);
        rfoChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        rfoChooser.setFileFilter(filter);

        final int returnVal = rfoChooser.showSaveDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            f = rfoChooser.getSelectedFile();
            result = "file:" + f.getAbsolutePath();

            mainFrame.getBuilder().saveRFOFile(result);
            return f.getName();
        }
        return "";
    }

    public String saveSCAD(final String fileRoot) {
        final File defaultFile = new File(fileRoot + ".scad");
        final JFileChooser scadChooser = new JFileChooser();
        scadChooser.setSelectedFile(defaultFile);
        scadChooser.setFileFilter(new FileNameExtensionFilter("OpenSCAD files", "scad"));
        scadChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        final int returnVal = scadChooser.showSaveDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = scadChooser.getSelectedFile();
            try {
                mainFrame.getBuilder().saveSCADFile(selectedFile);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            return selectedFile.getName();
        }
        return "";
    }

    public void mouseToWorld() {
        mainFrame.getBuilder().mouseToWorld();
    }

    public static GCodeExtruder getExtruder(final String material) {
        final GCodeExtruder extruder = gui.getPrinter().getExtruder(material);
        if (extruder == null) {
            LOGGER.error("null extruder for " + material);
        }
        return extruder;
    }

    public static void main(final String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("RepRap");
                try {
                    gui = new Main();
                    gui.createAndShowGUI();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
