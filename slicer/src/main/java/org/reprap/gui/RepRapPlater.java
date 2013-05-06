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
import java.awt.Dimension;
import java.awt.GraphicsConfigTemplate;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;

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
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.io.rfo.RFO;

import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;
import com.sun.j3d.utils.picking.PickTool;

/**
 * This class creates a virtual world of the RepRap working volume. It allows
 * you to put STL-file objects in it, move them about to arrange them, and build
 * them in the machine.
 */
public class RepRapPlater extends JPanel implements MouseListener {
    private MouseObject mouse;
    private PickCanvas pickCanvas; // The thing picked by a mouse click
    private STLObject lastPicked; // The last thing picked
    private final AllSTLsToBuild stls;
    private boolean reordering;
    private double xwv;
    private double ywv;
    private double zwv;
    private static final double RADIUS_FACTOR = 0.7;
    private static final double BACK_CLIP_FACTOR = 2.0;
    private static final double FRONT_FACTOR = 0.001;
    private static final double BOUNDS_FACTOR = 3.0;
    private static final Color3f BACKGROUND_COLOR = new Color3f(0.9f, 0.9f, 0.9f);
    private static final Color3f SELECTED_COLOR = new Color3f(0.6f, 0.2f, 0.2f);
    private static final Color3f MACHINE_COLOR = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);
    private final Appearance pickedAppearance = new Appearance();
    private final BranchGroup workingVolumeAndStls = new BranchGroup();
    private STLObject world;
    private STLObject workingVolume;
    private BranchGroup sceneBranchGroup;
    private final CurrentConfiguration currentConfiguration;

    public RepRapPlater(final CurrentConfiguration currentConfiguration) {
        this.currentConfiguration = currentConfiguration;
        initialise();
        stls = new AllSTLsToBuild();
        reordering = false;
        setPreferredSize(new Dimension(600, 400));
    }

    public AllSTLsToBuild getSTLs() {
        return stls;
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

        mouse = new MouseObject(createApplicationBounds());

        workingVolumeAndStls.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        workingVolumeAndStls.setCapability(Group.ALLOW_CHILDREN_WRITE);
        workingVolumeAndStls.setCapability(Group.ALLOW_CHILDREN_READ);

        // Load the STL file for the working volume
        world = new STLObject(workingVolumeAndStls);

        final Appearance workingVolumeAppearance = new Appearance();
        workingVolumeAppearance.setMaterial(new Material(MACHINE_COLOR, Constants.BLACK, MACHINE_COLOR, Constants.BLACK, 0f));
        workingVolume = new STLObject();
        workingVolume.loadIndependentSTL(baseFile, workingVolumeAppearance);
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
        final File file = original.fileAndDirectioryItCameFrom(0);
        final double increment = original.extent().x + 5;
        final Vector3d offset = new Vector3d();
        offset.x = increment;
        offset.y = 0;
        offset.z = 0;
        for (int i = 0; i < number; i++) {
            final STLObject stl = new STLObject();
            final Attributes newAtt = stl.addSTL(file, null);
            newAtt.setMaterial(originalAttributes.getMaterial());
            stl.translate(offset);
            if (stl.numChildren() > 0) {
                workingVolumeAndStls.addChild(stl.top());
                stls.add(stl);
            }
            offset.x += increment;
        }
    }

    public void anotherSTLFile(final File file, final boolean centre) {
        if (file == null) {
            return;
        }
        final STLObject stl = new STLObject();
        final Attributes att = stl.addSTL(file, lastPicked);
        if (lastPicked == null && centre) {

            final Point2D middle = Point2D.mul(0.5, new Point2D(200, 200));
            final Vector3d v = new Vector3d(middle.x(), middle.y(), 0);
            final Vector3d e = stl.extent();
            e.z = 0;
            e.x = -0.5 * e.x;
            e.y = -0.5 * e.y;
            v.add(e);
            stl.translate(v);
        }
        // New separate object, or just appended to lastPicked?
        if (stl.numChildren() > 0) {
            workingVolumeAndStls.addChild(stl.top());
            stls.add(stl);
        }

        MaterialRadioButtons.createAndShowGUI(att, this, stls.size() - 1, stl.volume());
    }

    // Callback for when the user has a pre-loaded STL and attribute
    public void anotherSTL(final STLObject stl, final Attributes att, final int index) {
        if (stl == null || att == null) {
            return;
        }

        // New separate object, or just appended to lastPicked?
        if (stl.numChildren() > 0) {
            workingVolumeAndStls.addChild(stl.top());
            stls.add(index, stl);
        }
    }

    public void changeMaterial() {
        if (lastPicked == null) {
            return;
        }
        MaterialRadioButtons.createAndShowGUI(lastPicked.attributes(0), this, lastPicked);
    }

    // Callback for when the user selects an RFO file to load
    public void addRFOFile(final File file) {
        if (file == null) {
            return;
        }
        AllSTLsToBuild newStls;
        try {
            newStls = RFO.load(file.getAbsolutePath());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < newStls.size(); i++) {
            workingVolumeAndStls.addChild(newStls.get(i).top());
        }
        stls.add(newStls);
    }

    public void saveRFOFile(final String s) throws IOException {
        RFO.save(s, stls);
    }

    public void saveSCADFile(final File selectedFile) throws IOException {
        stls.saveSCAD(selectedFile);
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
        if (stls.reorderAdd(lastPicked)) {
            return;
        }
        for (int i = 0; i < stls.size(); i++) {
            stls.get(i).restoreAppearance();
        }
        lastPicked = null;
        reordering = false;
    }

    public void nextPicked() {
        if (lastPicked == null) {
            lastPicked = stls.get(0);
        } else {
            lastPicked.restoreAppearance();
            lastPicked = stls.getNextOne(lastPicked);
        }
        lastPicked.setAppearance(pickedAppearance);
        mouse.move(lastPicked, true);
    }

    public void deleteSTL() {
        if (lastPicked == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < stls.size(); i++) {
            if (stls.get(i) == lastPicked) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            stls.remove(index);
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
        final Locale locale = new Locale(new VirtualUniverse());
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
}