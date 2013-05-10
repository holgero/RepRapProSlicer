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

import java.awt.HeadlessException;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

import org.reprap.configuration.Configuration;

public class MainFrame extends JFrame {
    private final Configuration configuration;
    private final RepRapPlater plater;

    public MainFrame() throws HeadlessException {
        super("RepRap Slicer");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        configuration = Configuration.create();
        plater = new RepRapPlater(configuration.getCurrentConfiguration());
    }

    public void createGui() {
        setJMenuBar(createMenu());
        getContentPane().add(createTabPane());
        pack();
        setVisible(true);
        setFocusable(true);
        requestFocus();
    }

    private JTabbedPane createTabPane() {
        final JTabbedPane tabPane = new JTabbedPane();
        tabPane.add("Plater", new PlaterPanel(configuration, plater));
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
        final JMenuItem quit = new JMenuItem(new AbstractAction("Exit") {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.dispose();
                dispose();
            }
        });
        fileMenu.add(quit);
        return fileMenu;
    }
}
