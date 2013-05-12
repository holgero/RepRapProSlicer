package org.reprap.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.STLObject;

/**
 * Radio button menu so you can set what material something is to be made from.
 * 
 * @author adrian
 */
class MaterialRadioButtons extends JPanel {
    private static final long serialVersionUID = 1L;
    private static Attributes att;
    private static JDialog dialog;
    private static JTextField copies;
    private static RepRapPlater rrb;
    private static int stlIndex;

    private MaterialRadioButtons(final CurrentConfiguration currentConfiguration, final double volume) {
        super(new BorderLayout());
        final JPanel radioPanel = new JPanel(new GridLayout(0, 1));
        radioPanel.setSize(300, 200);

        final JLabel jLabel0 = new JLabel();
        jLabel0.setText("Volume of object: " + Math.round(volume) + " mm^3");
        jLabel0.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(jLabel0);

        final JLabel jLabel2 = new JLabel();
        jLabel2.setText(" Number of copies of the object just loaded to print: ");
        jLabel2.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(jLabel2);

        copies = new JTextField("1");
        copies.setSize(20, 10);
        copies.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(copies);

        final JLabel jLabel1 = new JLabel();
        jLabel1.setText(" Select the material for the object(s): ");
        jLabel1.setHorizontalAlignment(SwingConstants.CENTER);
        radioPanel.add(jLabel1);

        final List<MaterialSetting> materials = currentConfiguration.getMaterials();
        final String matname = att.getMaterial();

        final ButtonGroup bGroup = new ButtonGroup();
        for (final MaterialSetting material : materials) {
            final JRadioButton b = new JRadioButton(material.getName());
            b.setActionCommand(material.getName());
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    final String materialName = e.getActionCommand();
                    att.setMaterial(materialName);
                    att.setAppearance(STLObject.createAppearance(material));
                }
            });
            if (matname.contentEquals(material.getName())) {
                b.setSelected(true);
            }
            bGroup.add(b);
            radioPanel.add(b);
        }

        final JButton okButton = new JButton();
        radioPanel.add(okButton);
        okButton.setText("OK");
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent evt) {
                OKHandler();
            }
        });

        add(radioPanel, BorderLayout.LINE_START);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    }

    private static void OKHandler() {
        final int number = Integer.parseInt(copies.getText().trim()) - 1;
        final STLObject stl = rrb.getSTLs().get(stlIndex);
        rrb.moreCopies(stl, att, number);
        dialog.dispose();
    }

    static void createAndShowGUI(final Attributes a, final RepRapPlater r, final int index, final double volume,
            final CurrentConfiguration currentConfiguration) {
        att = a;
        rrb = r;
        stlIndex = index;
        //Create and set up the window.
        dialog = new JDialog((JFrame) null, "Material selector");
        dialog.setLocation(500, 400);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        //Create and set up the content pane.
        final JComponent newContentPane = new MaterialRadioButtons(currentConfiguration, volume);
        newContentPane.setOpaque(true); //content panes must be opaque
        dialog.setContentPane(newContentPane);

        //Display the window.
        dialog.pack();
        dialog.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
        dialog.setVisible(true);
    }

    static void createAndShowGUI(final Attributes a, final RepRapPlater r, final STLObject lastPicked,
            final CurrentConfiguration currentConfiguration) {
        if (lastPicked == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < r.getSTLs().size(); i++) {
            if (r.getSTLs().get(i) == lastPicked) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            createAndShowGUI(a, r, index, r.getSTLs().get(index).volume(), currentConfiguration);
        }
    }
}