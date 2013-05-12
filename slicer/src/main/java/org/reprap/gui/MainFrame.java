/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.reprap.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.reprap.configuration.Configuration;
import org.reprap.geometry.Producer;
import org.reprap.geometry.ProductionProgressListener;

public class MainFrame extends JFrame {
    private final Configuration configuration;
    private final RepRapPlater plater;
    private final ActionMap actions;
    private final StatusBar statusBar = new StatusBar();
    private File currentFile;
    private final SimulationPanel simulationTab;

    public MainFrame() throws HeadlessException {
        super("RepRap Slicer");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        configuration = Configuration.create();
        plater = new RepRapPlater(configuration.getCurrentConfiguration());
        actions = new ActionMap(this, plater);
        simulationTab = new SimulationPanel(configuration.getCurrentConfiguration());
    }

    public void createGui() {
        setJMenuBar(createMenu());
        final Container contentPane = getContentPane();
        contentPane.add(createTabPane());
        contentPane.add(statusBar, BorderLayout.SOUTH);
        setCurrentFile(null);
        pack();
        setVisible(true);
        setFocusable(true);
        requestFocus();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(final WindowEvent e) {
                plater.dispose();
            }
        });
    }

    void setCurrentFile(final File file) {
        currentFile = file;
        final String text;
        if (isCurrentFileValid()) {
            text = currentFile.getName();
        } else {
            text = "";
        }
        statusBar.setMessage("Current file: " + text);
        actions.get(ActionMap.SAVE_RFO_ACTION).setEnabled(isCurrentFileValid());
        actions.get(ActionMap.SLICE_ACTION).setEnabled(isCurrentFileValid());
    }

    private boolean isCurrentFileValid() {
        return (currentFile != null) && currentFile.exists();
    }

    private static String stripExtension(final File file) {
        final String path = file.getAbsolutePath();
        return path.substring(0, path.length() - ".rfo".length());
    }

    File load(final String description, final String[] extensions, final String defaultName) {
        final FileFilter filter = new FileNameExtensionFilter(description, extensions);
        final JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(filter);
        if (!defaultName.isEmpty() && extensions.length == 1) {
            final File defaultFile = new File(defaultName + "." + extensions[0]);
            chooser.setSelectedFile(defaultFile);
        }

        final int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            final File result = chooser.getSelectedFile();
            if (extensions[0].toUpperCase().contentEquals("RFO")) {
                plater.loadRFOFile(result);
            }
            if (extensions[0].toUpperCase().contentEquals("STL")) {
                plater.anotherSTLFile(result);
            }
            return result;
        }
        return null;
    }

    private static File gcodeFileDialog(final File defaultFile) {
        final JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(defaultFile);
        final FileFilter filter = new FileNameExtensionFilter("G Code file to write to", new String[] { "gcode" });
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        final int result = chooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        } else {
            return null;
        }
    }

    void slice(final String gcodeFileName, final ProductionProgressListener listener, final boolean autoExit) {
        final File defaultFile = new File(gcodeFileName + ".gcode");
        final File gcodeFile;
        if (autoExit) {
            gcodeFile = defaultFile;
        } else {
            gcodeFile = gcodeFileDialog(defaultFile);
            if (gcodeFile == null) {
                return;
            }

        }
        actions.get(ActionMap.SLICE_ACTION).setEnabled(false);
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Producer");
                plater.mouseToWorld();
                final Producer producer = new Producer(gcodeFile, plater.getSTLs(), listener,
                        simulationTab.getSimulationPlotter(), configuration.getCurrentConfiguration());
                try {
                    producer.produce();
                    if (autoExit) {
                        System.exit(0);
                    }
                    JOptionPane.showMessageDialog(MainFrame.this, "Slicing complete");
                } catch (final RuntimeException e) {
                    JOptionPane.showMessageDialog(MainFrame.this, "Production exception: " + e);
                    throw e;
                } finally {
                    statusBar.setMessage("Sliced: " + currentFile.getName() + " to " + gcodeFile.getName());
                    actions.get(ActionMap.SLICE_ACTION).setEnabled(true);
                }
            }
        }.start();
    }

    private JTabbedPane createTabPane() {
        final JTabbedPane tabPane = new JTabbedPane();
        tabPane.addTab("Plater", null, new PlaterPanel(configuration, plater, actions), "Place things on the build platform.");
        tabPane.addTab("Print Settings", new PrintSettingsPanel());
        tabPane.addTab("Material Settings", new JPanel());
        tabPane.addTab("Printer Settings", new JPanel());
        tabPane.addTab("Visual Slicer", null, simulationTab, "Shows the current slice. " + "<space> to pause, "
                + "<b> to show boxes around polygons, " + "left click to magnify, "
                + "right click to restore default magnification.");
        tabPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(final ChangeEvent e) {
                simulationTab.hookListeners(simulationTab == tabPane.getSelectedComponent());
            }
        });
        return tabPane;
    }

    public void autoRun(final String fileName) {
        final File rfoFile = new File(fileName);
        plater.loadRFOFile(rfoFile);
        slice(stripExtension(rfoFile), new ProductionProgressListener() {
            @Override
            public void productionProgress(final int layer, final int totalLayers) {
                System.out.println(layer + "/" + totalLayers);
            }
        }, true);
    }

    private JMenuBar createMenu() {
        final JMenuBar menubar = new JMenuBar();
        menubar.add(createFileMenu());
        menubar.add(createPlateMenu());
        menubar.add(new JMenuItem(actions.get(ActionMap.SLICE_ACTION)));
        return menubar;
    }

    private JMenu createFileMenu() {
        final JMenu menu = new JMenu("File");
        menu.add(new JMenuItem(actions.get(ActionMap.LOAD_RFO_ACTION)));
        menu.add(new JMenuItem(actions.get(ActionMap.SAVE_RFO_ACTION)));
        menu.add(new JMenuItem(actions.get(ActionMap.LOAD_STL_CSG_ACTION)));
        menu.addSeparator();
        menu.add(new JMenuItem(actions.get(ActionMap.EXIT_ACTION)));
        return menu;
    }

    private JMenu createPlateMenu() {
        final JMenu menu = new JMenu("Plater");
        menu.add(new JMenuItem(actions.get(ActionMap.ROTATE_X_90)));
        menu.add(new JMenuItem(actions.get(ActionMap.ROTATE_Y_90)));
        menu.add(new JMenuItem(actions.get(ActionMap.ROTATE_Z_45)));
        menu.add(new JMenuItem(actions.get(ActionMap.ROTATE_Z_P_25)));
        menu.add(new JMenuItem(actions.get(ActionMap.ROTATE_Z_M_25)));
        menu.add(new JMenuItem(actions.get(ActionMap.CHANGE_MATERIAL)));
        menu.add(new JMenuItem(actions.get(ActionMap.SELECT_NEXT)));
        menu.add(new JMenuItem(actions.get(ActionMap.DELETE_OBJECT)));
        return menu;
    }

    void saveRFO() {
        final File defaultFile = new File(stripExtension(currentFile) + ".rfo");
        final JFileChooser rfoChooser = new JFileChooser();
        rfoChooser.setSelectedFile(defaultFile);
        rfoChooser.setFileFilter(new FileNameExtensionFilter("RFO files", "rfo"));
        rfoChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (rfoChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            plater.saveRFOFile(rfoChooser.getSelectedFile());
        }
    }

    void slice() {
        slice(stripExtension(currentFile), new StatusBarUpdater(statusBar, currentFile, simulationTab.getSimulationPlotter()),
                false);
    }
}
