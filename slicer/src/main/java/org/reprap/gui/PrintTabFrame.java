package org.reprap.gui;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

import org.reprap.configuration.CurrentConfiguration;

/**
 * @author ensab
 */
public class PrintTabFrame extends JInternalFrame {
    private static final long serialVersionUID = 1L;
    private File loadedFile;
    private boolean gcodeLoaded = false;
    private boolean slicing = false;
    private JLabel currentLayerOutOfN;
    private JLabel expectedBuildTime;
    private JLabel expectedFinishTime;
    private AbstractButton sliceButton;
    private JLabel fileNameBox;
    private JButton loadSTL;
    private JButton loadRFO;
    private JButton saveRFO;
    private JButton saveSCAD;
    private AbstractButton displayPathsCheck;
    private JProgressBar progressBar;
    private final MainFrame mainFrame;

    /**
     * Creates new form PrintTabFrame
     */
    PrintTabFrame(final MainFrame mainFrame, final CurrentConfiguration currentConfiguration) {
        this.mainFrame = mainFrame;
        initComponents(currentConfiguration);
    }

    private void initComponents(final CurrentConfiguration currentConfiguration) {
        final JButton helpButton = new JButton();
        helpButton.setActionCommand("Help");
        helpButton.setBackground(new java.awt.Color(255, 102, 255));
        helpButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                help();
            }
        });
        helpButton.setText("   Help   ");

        sliceButton = new JButton();
        sliceButton.setBackground(new java.awt.Color(51, 204, 0));
        sliceButton.setText("Slice");
        sliceButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                sliceButtonActionPerformed();
            }
        });

        loadSTL = new JButton();
        loadSTL.setBackground(new java.awt.Color(0, 204, 255));
        loadSTL.setText("Load STL/CSG");
        loadSTL.setActionCommand("loadSTL");
        loadSTL.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                loadSTL();
            }
        });

        loadRFO = new JButton();
        loadRFO.setBackground(new java.awt.Color(0, 204, 255));
        loadRFO.setText("Load RFO");
        loadRFO.setActionCommand("loadRFO");
        loadRFO.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                loadRFO();
            }
        });

        saveRFO = new JButton();
        saveRFO.setBackground(new java.awt.Color(0, 204, 255));
        saveRFO.setText("Save RFO");
        saveRFO.setActionCommand("saveRFO");
        saveRFO.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                try {
                    saveRFO();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        saveSCAD = new JButton();
        saveSCAD.setBackground(new java.awt.Color(0, 204, 255));
        saveSCAD.setText("Save SCAD");
        saveSCAD.setActionCommand("saveSCAD");
        saveSCAD.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                saveSCAD();
            }
        });

        final JButton exitButton = new JButton();
        exitButton.setText("Exit");
        exitButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                try {
                    exitButtonActionPerformed();
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        final JButton getWebPage = new JButton();
        getWebPage.setIcon(new ImageIcon(ClassLoader.getSystemResource("reprappro_logo-0.5.png")));
        getWebPage.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                getWebPageActionPerformed();
            }
        });

        final JLabel expectedBuildTimeLabel = new JLabel();
        expectedBuildTimeLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedBuildTimeLabel.setText("Expected slice time:");

        expectedBuildTime = new JLabel();
        expectedBuildTime.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedBuildTime.setText("00:00");

        final JLabel filesLabel = new JLabel();
        filesLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        filesLabel.setText("File(s): ");

        final JLabel expectedFinishTimeLabel = new JLabel();
        expectedFinishTimeLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedFinishTimeLabel.setText("Expected to finish at:");

        expectedFinishTime = new JLabel();
        expectedFinishTime.setFont(new java.awt.Font("Tahoma", 0, 12));
        expectedFinishTime.setText("    -");

        final JLabel changeMachineLabel = new JLabel();
        changeMachineLabel.setFont(new java.awt.Font("Tahoma", 0, 15));
        changeMachineLabel.setText("RepRap in use: " + currentConfiguration.getPrinterSetting().getName());

        final JLabel progressLabel = new JLabel();
        progressLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        progressLabel.setText("Top down layer progress:");

        currentLayerOutOfN = new JLabel();
        currentLayerOutOfN.setFont(new java.awt.Font("Tahoma", 0, 12));
        currentLayerOutOfN.setHorizontalAlignment(SwingConstants.RIGHT);
        currentLayerOutOfN.setText("000/000");

        progressBar = new JProgressBar();

        fileNameBox = new JLabel();
        fileNameBox.setFont(new java.awt.Font("Tahoma", 0, 12));
        fileNameBox.setText(" - ");

        displayPathsCheck = new JCheckBox();
        displayPathsCheck.setText("Show paths when slicing");
        createLayout(helpButton, exitButton, getWebPage, expectedBuildTimeLabel, filesLabel, expectedFinishTimeLabel,
                changeMachineLabel, progressLabel);
    }

    private void createLayout(final JButton helpButton, final JButton exitButton, final JButton getWebPage,
            final JLabel expectedBuildTimeLabel, final JLabel filesLabel, final JLabel expectedFinishTimeLabel,
            final JLabel changeMachineLabel, final JLabel progressLabel) {
        final org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);

        layout.setHorizontalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(layout
                                        .createSequentialGroup()
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(exitButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(sliceButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(saveRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(saveSCAD, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(loadRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                .add(loadSTL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 130,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                        .add(50, 50, 50)
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(displayPathsCheck)
                                                .add(changeMachineLabel)
                                                .add(layout
                                                        .createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                                                        .add(layout
                                                                .createSequentialGroup()
                                                                .add(helpButton,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                                                                        org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                                .add(100, 100, 100)
                                                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED,
                                                                        org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                                                        Short.MAX_VALUE)
                                                                .add(getWebPage,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 110,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED))
                                .add(layout
                                        .createSequentialGroup()
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(layout.createSequentialGroup().add(expectedBuildTimeLabel)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(expectedBuildTime))
                                                .add(layout.createSequentialGroup().add(expectedFinishTimeLabel).add(7, 7, 7)
                                                        .add(expectedFinishTime))
                                                .add(layout.createSequentialGroup().add(progressLabel).add(7, 7, 7)
                                                        .add(currentLayerOutOfN)))
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                                .add(layout
                                                        .createSequentialGroup()

                                                        .add(50, 50, 50)
                                                        .add(filesLabel)
                                                        .add(fileNameBox, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                                                org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                                .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 350,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
                        .addContainerGap(29, Short.MAX_VALUE)));

        layout.setVerticalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(layout
                                        .createSequentialGroup()
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(getWebPage, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 72,
                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                .add(layout
                                        .createSequentialGroup()
                                        .add(layout
                                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                .add(layout
                                                        .createSequentialGroup()
                                                        .add(loadRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(saveRFO, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(layout
                                                                .createParallelGroup()
                                                                .add(loadSTL, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                                                                        41, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                                .add(layout.createSequentialGroup().add(displayPathsCheck)))
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(saveSCAD, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 41,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(layout
                                                                .createParallelGroup()
                                                                .add(sliceButton,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 39,
                                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                                                                .add(layout.createSequentialGroup().add(changeMachineLabel)))
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                                        .add(exitButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 39,
                                                                org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                                .add(helpButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 45,
                                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING).add(
                                                layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING).add(
                                                        layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                                                .add(layout.createSequentialGroup()))))))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                                .add(expectedBuildTimeLabel)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                        .add(expectedBuildTime).add(filesLabel).add(fileNameBox)))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING).add(expectedFinishTimeLabel)
                                .add(expectedFinishTime))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout
                                .createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING).add(progressLabel)
                                        .add(currentLayerOutOfN))
                                .add(progressBar, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE,
                                        org.jdesktop.layout.GroupLayout.DEFAULT_SIZE,
                                        org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)).addContainerGap(24, Short.MAX_VALUE)));
    }

    private void printLive() {
        slicing = true;
        sliceButton.setText("Slicing...");
        sliceButton.setBackground(Color.gray);
    }

    private static boolean worthSaving() {
        return true;
    }

    private void sliceButtonActionPerformed() {
        if (slicing) {
            return;
        }
        if (loadedFile == null) {
            JOptionPane.showMessageDialog(null, "There are no STLs/RFOs loaded to slice to file.");
            return;
        }
        if (!isStlOrRfoFile(loadedFile)) {
            JOptionPane.showMessageDialog(null, "The loaded file is not an STL or an RFO file.");
            return;
        }
        if (mainFrame.slice(stripExtension(loadedFile), new ProgressBarUpdater(currentLayerOutOfN, progressBar,
                expectedBuildTime, expectedFinishTime, System.currentTimeMillis()), false, displayPathsCheck.isSelected())) {
            printLive();
        }
    }

    private static String stripExtension(final File file) {
        final String path = file.getAbsolutePath();
        return path.substring(0, path.length() - ".rfo".length());
    }

    private static boolean isStlOrRfoFile(final File file) {
        final String lowerName = file.getName().toLowerCase();
        return lowerName.endsWith(".stl") || lowerName.endsWith(".rfo");
    }

    private void exitButtonActionPerformed() throws IOException {
        if (worthSaving()) {
            final int toDo = JOptionPane.showConfirmDialog(null, "First save the build as an RFO file?");
            switch (toDo) {
            case JOptionPane.YES_OPTION:
                saveRFO();
                break;

            case JOptionPane.NO_OPTION:
                break;

            case JOptionPane.CANCEL_OPTION:
                return;

            default:
                saveRFO();
            }
        }
        System.exit(0);
    }

    private static void getWebPageActionPerformed() {
        try {
            final URI url = new URI("http://reprappro.com");
            Desktop.getDesktop().browse(url);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadSTL() {
        if (gcodeLoaded) {
            final int response = JOptionPane
                    .showOptionDialog(null, "This will abandon the G Code file you loaded.", "Load STL",
                            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[] { "OK", "Cancel" }, "");
            if (response == 1) {
                return;
            }
            loadedFile = null;
        }
        final File stlFile = mainFrame.onOpen("STL triangulation file", new String[] { "stl" }, "");
        loadedFile = stlFile;

        setFilenameBox();
    }

    private void loadRFO() {
        if (gcodeLoaded) {
            final int response = JOptionPane
                    .showOptionDialog(null, "This will abandon the previous GCode file you loaded.", "Load RFO",
                            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[] { "OK", "Cancel" }, "");
            if (response == 1) {
                return;
            }
            loadedFile = null;
        }

        final File rfoFile = mainFrame.onOpen("RFO multiple-object file", new String[] { "rfo" }, "");
        loadedFile = rfoFile;

        setFilenameBox();
    }

    private void setFilenameBox() {
        if (loadedFile != null) {
            fileNameBox.setText(loadedFile.getName());
        } else {
            fileNameBox.setText("");
        }
        gcodeLoaded = false;
    }

    private static void help() {
        try {
            final URI url = new URI("http://reprap.org/wiki/RepRapPro_Slicer");
            Desktop.getDesktop().browse(url);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveRFO() throws IOException {
        if (loadedFile == null) {
            JOptionPane.showMessageDialog(null, "There's nothing to save...");
            return;
        }
        if (!isStlOrRfoFile(loadedFile)) {
            JOptionPane.showMessageDialog(null, "The loaded file is not an STL or an RFO file.");
        }
        mainFrame.saveRFO(stripExtension(loadedFile));
    }

    private void saveSCAD() {
        if (loadedFile == null) {
            JOptionPane.showMessageDialog(null, "There's nothing to save...");
            return;
        }
        if (!isStlOrRfoFile(loadedFile)) {
            JOptionPane.showMessageDialog(null, "The loaded file is not an STL or an RFO file.");
        }
        mainFrame.saveSCAD(stripExtension(loadedFile));
    }

    void slicingFinished() {
        slicing = false;
        sliceButton.setText("Slice");
        sliceButton.setBackground(new java.awt.Color(51, 204, 0));
    }
}
