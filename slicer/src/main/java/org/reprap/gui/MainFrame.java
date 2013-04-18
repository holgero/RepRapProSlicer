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
import java.io.IOException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

public class MainFrame extends JFrame {
    private final JMenuItem produceProduceB;
    private final JMenuItem cancelMenuItem;
    private final JCheckBoxMenuItem layerPause;
    private final RepRapBuild builder;

    public MainFrame() throws HeadlessException, IOException {
        super("RepRap build bed    |     mouse:  left - rotate   middle - zoom   right - translate     |    grid: 20 mm");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        final JMenuBar menubar = new JMenuBar();
        menubar.add(createMenu());

        produceProduceB = new JMenuItem("Start build...", KeyEvent.VK_B);
        cancelMenuItem = new JMenuItem("Cancel", KeyEvent.VK_P);
        cancelMenuItem.setEnabled(false);
        layerPause = new JCheckBoxMenuItem("Pause before layer");

        final Box builderFrame = new Box(BoxLayout.Y_AXIS);
        builderFrame.add(new JLabel("Arrange items to print on the build bed"));
        builder = new RepRapBuild();
        org.reprap.attributes.Preferences.getInstance().registerPreferenceChangeListener(builder);
        builderFrame.setMinimumSize(new Dimension(0, 0));
        builderFrame.add(builder);
        builderFrame.setPreferredSize(new Dimension(1000, 800));
        getContentPane().add(builderFrame);

        setJMenuBar(menubar);

        pack();
        positionWindowOnScreen();
        setVisible(true);
        setFocusable(true);
        requestFocus();
    }

    private JMenu createMenu() {
        final JMenu manipMenu = new JMenu("Click here for help");
        manipMenu.setMnemonic(KeyEvent.VK_M);

        final JMenuItem manipX = new JMenuItem("Rotate X 90", KeyEvent.VK_X);
        manipX.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        manipX.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                builder.xRotate();
            }
        });
        manipMenu.add(manipX);

        final JMenuItem manipY = new JMenuItem("Rotate Y 90", KeyEvent.VK_Y);
        manipY.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK));
        manipY.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                builder.yRotate();
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
                builder.inToMM();
            }
        });
        manipMenu.add(inToMM);

        final JMenuItem changeMaterial = new JMenuItem("Change material", KeyEvent.VK_M);
        changeMaterial.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.CTRL_MASK));
        changeMaterial.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                builder.changeMaterial();
            }
        });
        manipMenu.add(changeMaterial);

        final JMenuItem nextP = new JMenuItem("Select next object that will be built", KeyEvent.VK_N);
        nextP.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        nextP.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                builder.nextPicked();
            }
        });
        manipMenu.add(nextP);

        final JMenuItem reorder = new JMenuItem("Reorder the building sequence", KeyEvent.VK_R);
        reorder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.CTRL_MASK));
        reorder.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                builder.doReorder();
            }
        });
        manipMenu.add(reorder);

        final JMenuItem deleteSTL = new JMenuItem("Delete selected object", KeyEvent.VK_DELETE);
        deleteSTL.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteSTL.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent arg0) {
                builder.deleteSTL();
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
        builder.zRotate(angle);
    }

    public JCheckBoxMenuItem getLayerPause() {
        return layerPause;
    }

    public void setLayerPause(final boolean state) {
        layerPause.setState(state);
    }

    public RepRapBuild getBuilder() {
        return builder;
    }

    public void producing(final boolean state) {
        cancelMenuItem.setEnabled(state);
        produceProduceB.setEnabled(!state);
    }
}
