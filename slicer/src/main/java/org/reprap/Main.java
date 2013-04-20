package org.reprap;

import java.io.IOException;

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
                try {
                    new MainFrame();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
