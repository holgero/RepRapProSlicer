package org.reprap.gui;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

/**
 * @author Ed Sells, March 2008
 */
public class SlicerFrame extends JFrame {
    private JTabbedPane jTabbedPane1;
    private PrintTabFrame printTabFrame1;

    public SlicerFrame(final MainFrame mainFrame) {
        initComponents(mainFrame);
        setTitle("RepRapPro Slicer");
        setVisible(true);
    }

    private void initComponents(final MainFrame mainFrame) {
        jTabbedPane1 = new javax.swing.JTabbedPane();
        printTabFrame1 = new PrintTabFrame(mainFrame);
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        jTabbedPane1.setRequestFocusEnabled(false);
        jTabbedPane1.addTab("Slice", printTabFrame1);

        final org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 670,
                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addContainerGap(5, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 430,
                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE).addContainerGap(5, Short.MAX_VALUE)));
        pack();
    }

    public void updateProgress() {
        printTabFrame1.updateProgress();
    }

    public boolean displayPaths() {
        return printTabFrame1.displayPaths();
    }
}
