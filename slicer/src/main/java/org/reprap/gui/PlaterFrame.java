/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 *   contains code extracted from org.reprap.Main, which contains
 *   no copyright notice.
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

import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.reprap.configuration.Configuration;
import org.reprap.geometry.Producer;
import org.reprap.geometry.ProductionProgressListener;

public class PlaterFrame extends JFrame {
    private final Configuration configuration = Configuration.create();
    private final JMenuItem produceProduceB;
    private final JMenuItem cancelMenuItem;
    private final RepRapPlater plater;
    private final JFileChooser chooser = new JFileChooser();
    private final SlicerFrame slicerFrame;

    public PlaterFrame() throws HeadlessException {
        super("RepRap build bed    |     mouse:  left - rotate   middle - zoom   right - translate     |    grid: 20 mm");
        JFrame.setDefaultLookAndFeelDecorated(false);
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        final JMenuBar menubar = new JMenuBar();
        menubar.add(createMenu());

        produceProduceB = new JMenuItem("Start build...", KeyEvent.VK_B);
        cancelMenuItem = new JMenuItem("Cancel", KeyEvent.VK_P);
        cancelMenuItem.setEnabled(false);

        final Box builderFrame = new Box(BoxLayout.Y_AXIS);
        builderFrame.add(new JLabel("Arrange items to print on the build bed"));
        plater = new RepRapPlater(configuration.getCurrentConfiguration());
        builderFrame.setMinimumSize(new Dimension(0, 0));
        builderFrame.add(plater);
        builderFrame.setPreferredSize(new Dimension(1000, 800));
        getContentPane().add(builderFrame);

        setJMenuBar(menubar);

        pack();
        positionWindowOnScreen();
        setVisible(true);
        setFocusable(true);
        requestFocus();

        slicerFrame = new SlicerFrame(this, configuration.getCurrentConfiguration());
    }

    public void autoRun(final String fileName) {
        final File rfoFile = new File(fileName);
        plater.loadRFOFile(rfoFile);
        final String rfoFileName = rfoFile.getAbsolutePath();
        slice(rfoFileName.substring(0, rfoFileName.length() - ".rfo".length()), new ProductionProgressListener() {
            @Override
            public void productionProgress(final int layer, final int totalLayers) {
                System.out.println(layer + "/" + totalLayers);
            }
        }, true, true);
    }

    private JMenu createMenu() {
        final JMenu manipMenu = new JMenu("Click here for help");
        manipMenu.setMnemonic(KeyEvent.VK_M);

        final JMenuItem manipX = new JMenuItem("Rotate X 90", KeyEvent.VK_X);
        manipX.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        manipX.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                plater.xRotate();
            }
        });
        manipMenu.add(manipX);

        final JMenuItem manipY = new JMenuItem("Rotate Y 90", KeyEvent.VK_Y);
        manipY.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK));
        manipY.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                plater.yRotate();
            }
        });
        manipMenu.add(manipY);

        final JMenuItem manipZ45 = new JMenuItem("Rotate Z 45", KeyEvent.VK_Z);
        manipZ45.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK));
        manipZ45.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateZ(45);
            }
        });
        manipMenu.add(manipZ45);

        final JMenuItem manipZp25 = new JMenuItem("Z Anticlockwise 2.5", KeyEvent.VK_A);
        manipZp25.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
        manipZp25.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateZ(2.5);
            }
        });
        manipMenu.add(manipZp25);

        final JMenuItem manipZm25 = new JMenuItem("Z Clockwise 2.5", KeyEvent.VK_C);
        manipZm25.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        manipZm25.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                onRotateZ(-2.5);
            }
        });
        manipMenu.add(manipZm25);

        final JMenuItem inToMM = new JMenuItem("Scale by 25.4 (in -> mm)", KeyEvent.VK_I);
        inToMM.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, ActionEvent.CTRL_MASK));
        inToMM.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                plater.inToMM();
            }
        });
        manipMenu.add(inToMM);

        final JMenuItem changeMaterial = new JMenuItem("Change material", KeyEvent.VK_M);
        changeMaterial.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.CTRL_MASK));
        changeMaterial.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                plater.changeMaterial();
            }
        });
        manipMenu.add(changeMaterial);

        final JMenuItem nextP = new JMenuItem("Select next object that will be built", KeyEvent.VK_N);
        nextP.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        nextP.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                plater.nextPicked();
            }
        });
        manipMenu.add(nextP);

        final JMenuItem reorder = new JMenuItem("Reorder the building sequence", KeyEvent.VK_R);
        reorder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
        reorder.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                plater.doReorder();
            }
        });
        manipMenu.add(reorder);

        final JMenuItem deleteSTL = new JMenuItem("Delete selected object", KeyEvent.VK_DELETE);
        deleteSTL.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteSTL.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                plater.deleteSTL();
            }
        });
        manipMenu.add(deleteSTL);
        return manipMenu;
    }

    private void positionWindowOnScreen() {
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenSize.width - getSize().width) / 2, (screenSize.height - getSize().height) / 2);
    }

    private void onRotateZ(final double angle) {
        plater.zRotate(angle);
    }

    private void producing(final boolean state) {
        cancelMenuItem.setEnabled(state);
        produceProduceB.setEnabled(!state);
    }

    File onOpen(final String description, final String[] extensions, final String defaultRoot) {
        final FileFilter filter = new FileNameExtensionFilter(description, extensions);

        chooser.setFileFilter(filter);
        if (!defaultRoot.contentEquals("") && extensions.length == 1) {
            final File defaultFile = new File(defaultRoot + "." + extensions[0]);
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

    void saveSCAD(final String fileRoot) {
        final File defaultFile = new File(fileRoot + ".scad");
        final JFileChooser scadChooser = new JFileChooser();
        scadChooser.setSelectedFile(defaultFile);
        scadChooser.setFileFilter(new FileNameExtensionFilter("OpenSCAD files", "scad"));
        scadChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (scadChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            plater.saveSCADFile(scadChooser.getSelectedFile());
        }
    }

    private File gcodeFileDialog(final File defaultFile) {
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

    boolean slice(final String gcodeFileName, final ProductionProgressListener listener, final boolean autoExit,
            final boolean displayPaths) {
        final File defaultFile = new File(gcodeFileName + ".gcode");
        final File gcodeFile;
        if (autoExit) {
            gcodeFile = defaultFile;
        } else {
            gcodeFile = gcodeFileDialog(defaultFile);
            if (gcodeFile == null) {
                return false;
            }

        }
        producing(true);
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Producer");
                plater.mouseToWorld();
                final Producer producer = new Producer(gcodeFile, plater.getSTLs(), listener, displayPaths,
                        configuration.getCurrentConfiguration());
                try {
                    producer.produce();
                    if (autoExit) {
                        System.exit(0);
                    }
                    JOptionPane.showMessageDialog(PlaterFrame.this, "Slicing complete");
                } catch (final RuntimeException e) {
                    JOptionPane.showMessageDialog(PlaterFrame.this, "Production exception: " + e);
                    throw e;
                } finally {
                    producing(false);
                    slicerFrame.slicingFinished();
                    producer.dispose();
                }
            }
        }.start();
        return true;
    }
}