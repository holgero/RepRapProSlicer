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

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;

public class ActionMap {
    static final String LOAD_RFO_ACTION = "Load RFO";
    static final String SAVE_RFO_ACTION = "Save RFO";
    static final String LOAD_STL_CSG_ACTION = "Load STL/CSG";
    static final String EXIT_ACTION = "Exit";
    static final String SLICE_ACTION = "Slice";
    static final String ROTATE_X_90 = "Rotate X 90";
    static final String ROTATE_Y_90 = "Rotate Y 90";
    static final String ROTATE_Z_45 = "Rotate Z 45";
    static final String ROTATE_Z_P_25 = "Rotate Z +2.5";
    static final String ROTATE_Z_M_25 = "Rotate Z -2.5";
    static final String CHANGE_MATERIAL = "Change material";
    static final String SELECT_NEXT = "Select next";
    static final String DELETE_OBJECT = "Delete object";

    private final Map<String, Action> actions = new HashMap<String, Action>();
    private final MainFrame mainFrame;
    private final RepRapPlater plater;

    public ActionMap(final MainFrame mainFrame, final RepRapPlater plater) {
        this.mainFrame = mainFrame;
        this.plater = plater;
        createActions();
    }

    private void createActions() {
        actions.put(LOAD_RFO_ACTION, new AbstractAction(LOAD_RFO_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final File file = mainFrame.load("RFO multiple-object file", new String[] { "rfo" }, "");
                mainFrame.setCurrentFile(file);
            }
        });
        actions.put(SAVE_RFO_ACTION, new AbstractAction(SAVE_RFO_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                mainFrame.saveRFO();
            }
        });
        actions.put(LOAD_STL_CSG_ACTION, new AbstractAction(LOAD_STL_CSG_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final File file = mainFrame.load("STL triangulation file", new String[] { "stl" }, "");
                mainFrame.setCurrentFile(file);
            }
        });
        actions.put(SLICE_ACTION, new AbstractAction(SLICE_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                mainFrame.slice();
            }
        });
        actions.put(EXIT_ACTION, new AbstractAction(EXIT_ACTION) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                mainFrame.dispose();
            }
        });
        actions.put(ROTATE_X_90, new AbstractAction(ROTATE_X_90) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.xRotate();
            }
        });
        actions.put(ROTATE_Y_90, new AbstractAction(ROTATE_Y_90) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.yRotate();
            }
        });
        actions.put(ROTATE_Z_45, new AbstractAction(ROTATE_Z_45) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.zRotate(45);
            }
        });
        actions.put(ROTATE_Z_P_25, new AbstractAction(ROTATE_Z_P_25) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.zRotate(2.5);
            }
        });
        actions.put(ROTATE_Z_M_25, new AbstractAction(ROTATE_Z_M_25) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.zRotate(-2.5);
            }
        });
        actions.put(CHANGE_MATERIAL, new AbstractAction(CHANGE_MATERIAL) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.changeMaterial();
            }
        });
        actions.put(SELECT_NEXT, new AbstractAction(SELECT_NEXT) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.nextPicked();
            }
        });
        actions.put(DELETE_OBJECT, new AbstractAction(DELETE_OBJECT) {
            @Override
            public void actionPerformed(final ActionEvent e) {
                plater.deleteSTL();
            }
        });
    }

    Action get(final String key) {
        return actions.get(key);
    }
}
