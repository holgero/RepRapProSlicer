package org.reprap;

import javax.swing.SwingUtilities;

import org.reprap.gui.MainFrame;

/**
 * Main RepRapProSlicer software overview. Please see http://reprap.org/ for
 * more details.
 */
public class Main {
    public static void main(final String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final MainFrame main = new MainFrame();
                if (args.length == 1) {
                    main.autoRun(args[0]);
                }
            }
        });
    }
}
