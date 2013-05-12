/*
 
 RepRap
 ------
 
 The Replicating Rapid Prototyper Project
 
 
 Copyright (C) 2006
 Adrian Bowyer & The University of Bath
 
 http://reprap.org
 
 Principal author:
 
 Adrian Bowyer
 Department of Mechanical Engineering
 Faculty of Engineering and Design
 University of Bath
 Bath BA2 7AY
 U.K.
 
 e-mail: A.Bowyer@bath.ac.uk
 
 RepRap is free; you can redistribute it and/or
 modify it under the terms of the GNU Library General Public
 Licence as published by the Free Software Foundation; either
 version 2 of the Licence, or (at your option) any later version.
 
 RepRap is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Library General private Licence for more details.
 
 For this purpose the words "software" and "library" in the GNU Library
 General private Licence are taken to mean any and all computer programs
 computer files data results documents and other copyright information
 available from the RepRap project.
 
 You should have received a copy of the GNU Library General Public
 Licence along with RepRap; if not, write to the Free
 Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA,
 or see
 
 http://www.gnu.org/
 
 =====================================================================
 
 This program loads STL files of objects, orients them, and builds them
 in the RepRap machine.
 
 It is based on one of the open-source examples in Daniel Selman's excellent
 Java3D book, and his notice is immediately below.
 
 First version 2 April 2006
 This version: 16 April 2006
 
 */

/*******************************************************************************
 * VrmlPickingTest.java Copyright (C) 2001 Daniel Selman
 * 
 * First distributed with the book "Java 3D Programming" by Daniel Selman and
 * published by Manning Publications. http://manning.com/selman
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General private License as published by the Free Software
 * Foundation, version 2.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General private License for more
 * details.
 * 
 * The license can be found on the WWW at: http://www.fsf.org/copyleft/gpl.html
 * 
 * Or by writing to: Free Software Foundation, Inc., 59 Temple Place - Suite
 * 330, Boston, MA 02111-1307, USA.
 * 
 * Authors can be contacted at: Daniel Selman: daniel@selman.org
 * 
 * If you make changes you think others would like, please contact one of the
 * authors or someone at the www.j3d.org web site.
 ******************************************************************************/

package org.reprap.gui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsConfigTemplate;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.Bounds;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GraphicsConfigTemplate3D;
import javax.media.j3d.Group;
import javax.media.j3d.Locale;
import javax.media.j3d.Material;
import javax.media.j3d.Node;
import javax.media.j3d.PhysicalBody;
import javax.media.j3d.PhysicalEnvironment;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.View;
import javax.media.j3d.ViewPlatform;
import javax.media.j3d.VirtualUniverse;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.PrinterSetting;
import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.Attributes;
import org.reprap.geometry.polyhedra.Constants;
import org.reprap.geometry.polyhedra.STLFileContents;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.geometry.polyhedra.STLObjectMouseMover;
import org.reprap.io.rfo.RFO;
import org.reprap.io.stl.StlFileLoader;

import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;
import com.sun.j3d.utils.picking.PickTool;

/**
 * This class creates a virtual world of the RepRap working volume. It allows
 * you to put STL-file objects in it, move them about to arrange them, and build
 * them in the machine.
 */
public class RepRapPlater extends JPanel implements MouseListener {
    private static final double RADIUS_FACTOR = 0.7;
    private static final double BACK_CLIP_FACTOR = 2.0;
    private static final double FRONT_FACTOR = 0.001;
    private static final double BOUNDS_FACTOR = 3.0;
    private static final Color3f BACKGROUND_COLOR = new Color3f(0.9f, 0.9f, 0.9f);
    private static final Color3f SELECTED_COLOR = new Color3f(0.6f, 0.2f, 0.2f);
    private static final Color3f MACHINE_COLOR = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);

    private final CurrentConfiguration currentConfiguration;
    private final VirtualUniverse virtualUniverse = new VirtualUniverse();
    private final Appearance pickedAppearance = new Appearance();
    private final BranchGroup workingVolumeAndStls = new BranchGroup();
    private STLObjectMouseMover mouse;
    private PickCanvas pickCanvas; // The thing picked by a mouse click
    private STLObject lastPicked; // The last thing picked
    private RFO rfo; // the current project
    private boolean reordering;
    private double xwv;
    private double ywv;
    private double zwv;
    private STLObject world;
    private STLObject workingVolume;
    private BranchGroup sceneBranchGroup;

    public RepRapPlater(final CurrentConfiguration currentConfiguration) {
        this.currentConfiguration = currentConfiguration;
        initialise();
        rfo = new RFO(currentConfiguration);
        reordering = false;
        setPreferredSize(new Dimension(600, 400));
    }

    public void dispose() {
        virtualUniverse.removeAllLocales();
        try {
            // give the Java3D threads the chance to terminate peacefully.
            Thread.sleep(250);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // now become rude with those that didn't take the chance
        finishJava3dThreads();
    }

    private static void finishJava3dThreads() {
        final Class<?> rendererClass;
        final Method finishMethod;
        try {
            rendererClass = Class.forName("javax.media.j3d.J3dThread");
            finishMethod = rendererClass.getDeclaredMethod("finish");
            finishMethod.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
        final ThreadGroup[] groups = new ThreadGroup[10];
        final int count = Thread.currentThread().getThreadGroup().getParent().enumerate(groups);
        for (int i = 0; i < count; i++) {
            final ThreadGroup threadGroup = groups[i];
            if ("Java3D".equals(threadGroup.getName())) {
                threadGroup.setDaemon(true);
                final Thread[] threads = new Thread[threadGroup.activeCount()];
                final int threadCount = threadGroup.enumerate(threads);
                for (int j = 0; j < threadCount; j++) {
                    final Thread thread = threads[j];
                    if (rendererClass.isInstance(thread)) {
                        try {
                            finishMethod.invoke(thread);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                Thread.yield();
                threadGroup.interrupt();
            }
        }
    }

    private Background createBackground() {
        final Background background = new Background(BACKGROUND_COLOR);
        background.setApplicationBounds(createApplicationBounds());
        return background;
    }

    private static BranchGroup createViewBranchGroup(final TransformGroup[] tgArray, final ViewPlatform vp) {
        final BranchGroup vpBranchGroup = new BranchGroup();

        if (tgArray != null && tgArray.length > 0) {
            Group parentGroup = vpBranchGroup;
            TransformGroup curTg = null;

            for (final TransformGroup element : tgArray) {
                curTg = element;
                parentGroup.addChild(curTg);
                parentGroup = curTg;
            }

            tgArray[tgArray.length - 1].addChild(vp);
        } else {
            vpBranchGroup.addChild(vp);
        }

        return vpBranchGroup;
    }

    private BranchGroup createSceneBranchGroup(final File baseFile) {
        final BranchGroup objRoot = new BranchGroup();
        final Bounds lightBounds = createApplicationBounds();
        final AmbientLight ambLight = new AmbientLight(true, new Color3f(1.0f, 1.0f, 1.0f));
        ambLight.setInfluencingBounds(lightBounds);
        objRoot.addChild(ambLight);

        final DirectionalLight headLight = new DirectionalLight();
        headLight.setInfluencingBounds(lightBounds);
        objRoot.addChild(headLight);

        mouse = new STLObjectMouseMover(createApplicationBounds());

        workingVolumeAndStls.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        workingVolumeAndStls.setCapability(Group.ALLOW_CHILDREN_WRITE);
        workingVolumeAndStls.setCapability(Group.ALLOW_CHILDREN_READ);

        world = new STLObject(workingVolumeAndStls);

        final Appearance workingVolumeAppearance = new Appearance();
        workingVolumeAppearance.setMaterial(new Material(MACHINE_COLOR, Constants.BLACK, MACHINE_COLOR, Constants.BLACK, 0f));
        // Load the STL file for the working volume
        final STLFileContents stlFileContents = StlFileLoader.loadSTLFileContents(baseFile);
        workingVolume = STLObject.loadIndependentSTL(stlFileContents, workingVolumeAppearance);
        workingVolumeAndStls.addChild(workingVolume.top());

        // Set the mouse to move everything
        mouse.move(world, false);
        objRoot.addChild(world.top());

        return objRoot;
    }

    // Action on mouse click
    @Override
    public void mouseClicked(final MouseEvent e) {
        pickCanvas.setShapeLocation(e);

        final PickResult pickResult = pickCanvas.pickClosest();

        if (pickResult != null) {
            final Node actualNode = pickResult.getObject();

            final Attributes att = (Attributes) actualNode.getUserData();
            final STLObject picked = att.getParent();
            if (picked != null) {
                if (picked != workingVolume) {
                    picked.setAppearance(pickedAppearance); // Highlight it
                    if (lastPicked != null && !reordering) {
                        lastPicked.restoreAppearance(); // lowlight
                    }
                    if (!reordering) {
                        mouse.move(picked, true); // Set the mouse to move it
                    }
                    lastPicked = picked;
                    reorder();
                } else {
                    if (!reordering) {
                        mouseToWorld();
                    }
                }
            }
        }
    }

    public void mouseToWorld() {
        if (lastPicked != null) {
            lastPicked.restoreAppearance();
        }
        mouse.move(world, false);
        lastPicked = null;
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
    }

    @Override
    public void mouseExited(final MouseEvent e) {
    }

    @Override
    public void mousePressed(final MouseEvent e) {
    }

    @Override
    public void mouseReleased(final MouseEvent e) {
    }

    void moreCopies(final STLObject original, final Attributes originalAttributes, final int number) {
        if (number <= 0) {
            return;
        }
        final File file = original.getSourceFile(0);
        final double increment = original.extent().x + 5;
        final Vector3d offset = new Vector3d();
        offset.x = increment;
        offset.y = 0;
        offset.z = 0;
        for (int i = 0; i < number; i++) {
            final STLFileContents stlFileContents = StlFileLoader.loadSTLFileContents(file);
            final STLObject stl = STLObject.createStlObjectFromFile(stlFileContents, originalAttributes.getMaterial(),
                    currentConfiguration);
            stl.translate(offset);
            if (stl.numChildren() > 0) {
                workingVolumeAndStls.addChild(stl.top());
                getSTLs().add(stl);
            }
            offset.x += increment;
        }
    }

    public void anotherSTLFile(final File file) {
        if (file == null) {
            return;
        }

        final STLFileContents stlFileContents = StlFileLoader.loadSTLFileContents(file);
        final STLObject stl;
        final String defaultMaterial = currentConfiguration.getMaterials().get(0).getName();
        if (lastPicked == null) {
            stl = STLObject.createStlObjectFromFile(stlFileContents, defaultMaterial, currentConfiguration);
            final Point2D middle = Point2D.mul(0.5, new Point2D(200, 200));
            final Vector3d v = new Vector3d(middle.x(), middle.y(), 0);
            final Vector3d e = stl.extent();
            e.z = 0;
            e.x = -0.5 * e.x;
            e.y = -0.5 * e.y;
            v.add(e);
            stl.translate(v);
            workingVolumeAndStls.addChild(stl.top());
            getSTLs().add(stl);
        } else {
            stl = lastPicked;
            stl.addSTL(stlFileContents, defaultMaterial, currentConfiguration);
        }

        showMaterialChooserDialog(stl.attributes(stl.size() - 1), getSTLs().size() - 1);
    }

    // Callback for when the user has a pre-loaded STL and attribute
    public void anotherSTL(final STLObject stl, final Attributes att, final int index) {
        if (stl == null || att == null) {
            return;
        }

        // New separate object, or just appended to lastPicked?
        if (stl.numChildren() > 0) {
            workingVolumeAndStls.addChild(stl.top());
            getSTLs().add(index, stl);
        }
    }

    public void changeMaterial() {
        if (lastPicked == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < getSTLs().size(); i++) {
            if (getSTLs().get(i) == lastPicked) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            showMaterialChooserDialog(lastPicked.attributes(0), index);
        }
    }

    // Callback for when the user selects an RFO file to load
    public void loadRFOFile(final File file) {
        if (file == null) {
            return;
        }
        rfo = new RFO(getSTLs(), currentConfiguration);
        rfo.load(file);
        final AllSTLsToBuild newStls = rfo.getAllStls();
        for (int i = 0; i < newStls.size(); i++) {
            final BranchGroup top = newStls.get(i).top();
            if (workingVolumeAndStls.indexOfChild(top) == -1) {
                workingVolumeAndStls.addChild(top);
            }
        }
    }

    public void saveRFOFile(final File file) {
        if (!checkFile(file)) {
            return;
        }
        try {
            rfo.save(file);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveSCADFile(final File file) {
        if (!checkFile(file)) {
            return;
        }
        final File directory = file.getParentFile();
        if (!directory.exists()) {
            directory.mkdir();
        }
        try {
            final AllSTLsToBuild allSTLs = getSTLs();
            RFO.copySTLs(allSTLs, directory);
            allSTLs.saveSCAD(file);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addCanvas3D(final Canvas3D canvas3d) {
        setLayout(new BorderLayout());
        add(canvas3d, BorderLayout.CENTER);
        doLayout();
        canvas3d.addMouseListener(this);
        pickCanvas = new PickCanvas(canvas3d, sceneBranchGroup);
        pickCanvas.setMode(PickTool.GEOMETRY_INTERSECT_INFO);
        pickCanvas.setTolerance(4.0f);
        canvas3d.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    public void xRotate() {
        if (lastPicked != null) {
            lastPicked.xClick();
        }
    }

    public void yRotate() {
        if (lastPicked != null) {
            lastPicked.yClick();
        }
    }

    public void zRotate(final double angle) {
        if (lastPicked != null) {
            lastPicked.zClick(angle);
        }
    }

    public void inToMM() {
        if (lastPicked != null) {
            lastPicked.inToMM();
        }
    }

    public void doReorder() {
        if (lastPicked != null) {
            lastPicked.restoreAppearance();
            mouseToWorld();
            lastPicked = null;
        }
        reordering = true;
    }

    private void reorder() {
        if (!reordering) {
            return;
        }
        if (getSTLs().reorderAdd(lastPicked)) {
            return;
        }
        for (int i = 0; i < getSTLs().size(); i++) {
            getSTLs().get(i).restoreAppearance();
        }
        lastPicked = null;
        reordering = false;
    }

    public void nextPicked() {
        if (lastPicked == null) {
            lastPicked = getSTLs().get(0);
        } else {
            lastPicked.restoreAppearance();
            lastPicked = getSTLs().getNextOne(lastPicked);
        }
        lastPicked.setAppearance(pickedAppearance);
        mouse.move(lastPicked, true);
    }

    public void deleteSTL() {
        if (lastPicked == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < getSTLs().size(); i++) {
            if (getSTLs().get(i) == lastPicked) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            getSTLs().remove(index);
            index = workingVolumeAndStls.indexOfChild(lastPicked.top());
            mouseToWorld();
            workingVolumeAndStls.removeChild(index);
            lastPicked = null;
        }
    }

    private void initialise() {
        final PrinterSetting printerSetting = currentConfiguration.getPrinterSetting();
        xwv = printerSetting.getBedSizeX();
        ywv = printerSetting.getBedSizeY();
        zwv = printerSetting.getMaximumZ();
        pickedAppearance.setMaterial(new Material(SELECTED_COLOR, Constants.BLACK, SELECTED_COLOR, Constants.BLACK, 0f));
        sceneBranchGroup = createSceneBranchGroup(printerSetting.getBuildPlatformStl());
        final ViewPlatform viewPlatform = createViewPlatform();
        final BranchGroup viewBranchGroup = createViewBranchGroup(getViewTransformGroupArray(), viewPlatform);
        createView(viewPlatform);
        final Background background = createBackground();
        sceneBranchGroup.addChild(background);
        final Locale locale = new Locale(virtualUniverse);
        locale.addBranchGraph(sceneBranchGroup);
        locale.addBranchGraph(viewBranchGroup);
    }

    private Bounds createApplicationBounds() {
        return new BoundingSphere(new Point3d(xwv * 0.5, ywv * 0.5, zwv * 0.5), BOUNDS_FACTOR
                * getViewPlatformActivationRadius());
    }

    private float getViewPlatformActivationRadius() {
        return (float) (RADIUS_FACTOR * Math.sqrt(xwv * xwv + ywv * ywv + zwv * zwv));
    }

    private void createView(final ViewPlatform viewPlatform) {
        final View view = new View();
        view.setPhysicalEnvironment(new PhysicalEnvironment());
        view.setPhysicalBody(new PhysicalBody());
        view.attachViewPlatform(viewPlatform);
        view.setBackClipDistance(BACK_CLIP_FACTOR * getViewPlatformActivationRadius());
        view.setFrontClipDistance(FRONT_FACTOR * getViewPlatformActivationRadius());

        final Canvas3D canvas3d = createCanvas3D();
        view.addCanvas3D(canvas3d);
        addCanvas3D(canvas3d);
    }

    private static Canvas3D createCanvas3D() {
        final GraphicsConfigTemplate3D gc3D = new GraphicsConfigTemplate3D();
        gc3D.setSceneAntialiasing(GraphicsConfigTemplate.PREFERRED);
        final GraphicsDevice gd[] = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();

        return new Canvas3D(gd[0].getBestConfiguration(gc3D));
    }

    private ViewPlatform createViewPlatform() {
        final ViewPlatform viewPlatform = new ViewPlatform();
        viewPlatform.setViewAttachPolicy(View.RELATIVE_TO_FIELD_OF_VIEW);
        viewPlatform.setActivationRadius(getViewPlatformActivationRadius());

        return viewPlatform;
    }

    private TransformGroup[] getViewTransformGroupArray() {
        final TransformGroup[] tgArray = new TransformGroup[1];
        tgArray[0] = new TransformGroup();

        final Transform3D viewTrans = new Transform3D();
        final Transform3D eyeTrans = new Transform3D();

        final BoundingSphere sceneBounds = (BoundingSphere) sceneBranchGroup.getBounds();

        // point the view at the center of the object
        final Point3d center = new Point3d();
        sceneBounds.getCenter(center);
        final double radius = sceneBounds.getRadius();
        final Vector3d temp = new Vector3d(center);
        viewTrans.set(temp);

        // pull the eye back far enough to see the whole object
        final double eyeDist = radius / Math.tan(Math.toRadians(40) / 2.0);
        temp.x = 0.0;
        temp.y = 0.0;
        temp.z = eyeDist;
        eyeTrans.set(temp);
        viewTrans.mul(eyeTrans);

        tgArray[0].setTransform(viewTrans);

        return tgArray;
    }

    /**
     * Warn the user of an overwrite
     */
    public static boolean checkFile(final File file) {
        if (file.exists()) {
            final String[] options = { "OK", "Cancel" };
            return JOptionPane.showOptionDialog(null, "The file " + file.getName() + " exists.  Overwrite it?", "Warning",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]) == 0;
        }
        return true;
    }

    public AllSTLsToBuild getSTLs() {
        return rfo.getAllStls();
    }

    private void showMaterialChooserDialog(final Attributes attributes, final int index) {
        final STLObject stl = getSTLs().get(index);
        final JDialog dialog = new MaterialChooserDialog(attributes, currentConfiguration, stl.volume(), this, index);
        dialog.pack();
        dialog.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
        dialog.setVisible(true);
    }
}