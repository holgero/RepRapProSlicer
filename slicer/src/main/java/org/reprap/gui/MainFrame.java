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
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.reprap.configuration.Configuration;

public class MainFrame extends JFrame {
    static final String LOAD_RFO_ACTION = "Load RFO";
    static final String SAVE_RFO_ACTION = "Save RFO";
    static final String LOAD_STL_CSG_ACTION = "Load STL/CSG";
    static final String SLICE_ACTION = "Slice";
    static final String EXIT_ACTION = "Exit";

    private final Configuration configuration;
    private final RepRapPlater plater;
    private final Map<String, Action> actions = new HashMap<String, Action>();
    private final StatusBar statusBar = new StatusBar();
    private File currentFile;

    public MainFrame() throws HeadlessException {
        super("RepRap Slicer");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        configuration = Configuration.create();
        plater = new RepRapPlater(configuration.getCurrentConfiguration());
        createActions();
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

    private void setCurrentFile(final File file) {
        currentFile = file;
        final String text;
        if (file != null) {
            text = file.getName();
        } else {
            text = "";
        }
        statusBar.setMessage("Current file: " + text);
    }

    private void createActions() {
        actions.put(LOAD_RFO_ACTION, new AbstractAction(LOAD_RFO_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final File file = load("RFO multiple-object file", new String[] { "rfo" }, "");
                setCurrentFile(file);
            }
        });
        actions.put(SAVE_RFO_ACTION, new AbstractAction(SAVE_RFO_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                if (currentFile == null) {
                    JOptionPane.showMessageDialog(null, "There's nothing to save...");
                    return;
                }
                if (!isStlOrRfoFile(currentFile)) {
                    JOptionPane.showMessageDialog(null, "The loaded file is not an STL or an RFO file.");
                }
                saveRFO(stripExtension(currentFile));
            }

            private boolean isStlOrRfoFile(final File file) {
                final String lowerName = file.getName().toLowerCase();
                return lowerName.endsWith(".stl") || lowerName.endsWith(".rfo");
            }

            private String stripExtension(final File file) {
                final String path = file.getAbsolutePath();
                return path.substring(0, path.length() - ".rfo".length());
            }
        });
        actions.put(LOAD_STL_CSG_ACTION, new AbstractAction(LOAD_STL_CSG_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final File file = load("STL triangulation file", new String[] { "stl" }, "");
                setCurrentFile(file);
            }
        });
        actions.put(SLICE_ACTION, new AbstractAction(SLICE_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                // TODO Auto-generated method stub
                System.out.println(SLICE_ACTION);
            }
        });
        actions.put(EXIT_ACTION, new AbstractAction(EXIT_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                dispose();
            }
        });
    }

    private File load(final String description, final String[] extensions, final String defaultName) {
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

    void saveRFO(final String fileRoot) {
        final File defaultFile = new File(fileRoot + ".rfo");
        final JFileChooser rfoChooser = new JFileChooser();
        rfoChooser.setSelectedFile(defaultFile);
        rfoChooser.setFileFilter(new FileNameExtensionFilter("RFO files", "rfo"));
        rfoChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (rfoChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            plater.saveRFOFile(rfoChooser.getSelectedFile());
        }
    }

    private JTabbedPane createTabPane() {
        final JTabbedPane tabPane = new JTabbedPane();
        tabPane.add("Plater", new PlaterPanel(configuration, plater, actions));
        tabPane.add("Print Settings", new PrintSettingsPanel());
        tabPane.add("Material Settings", new JPanel());
        tabPane.add("Printer Settings", new JPanel());
        tabPane.add("Visual Slicer", new JPanel());
        return tabPane;
    }

    public void autoRun(final String fileName) {
        new PlaterFrame().autoRun(fileName);
    }

    private JMenuBar createMenu() {
        final JMenuBar menubar = new JMenuBar();
        final JMenu fileMenu = createFileMenu();
        menubar.add(fileMenu);
        return menubar;
    }

    private JMenu createFileMenu() {
        final JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem(actions.get(LOAD_RFO_ACTION)));
        fileMenu.add(new JMenuItem(actions.get(SAVE_RFO_ACTION)));
        fileMenu.add(new JMenuItem(actions.get(LOAD_STL_CSG_ACTION)));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(actions.get(EXIT_ACTION)));
        return fileMenu;
    }
}
