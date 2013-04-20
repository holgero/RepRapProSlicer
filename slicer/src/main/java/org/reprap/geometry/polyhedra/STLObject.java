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
Library General Public Licence for more details.
 
For this purpose the words "software" and "library" in the GNU Library
General Public Licence are taken to mean any and all computer programs
computer files data results documents and other copyright information
available from the RepRap project.
 
You should have received a copy of the GNU Library General Public
Licence along with RepRap; if not, write to the Free
Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA,
or see
 
http://www.gnu.org/
 
=====================================================================
 
Wrapper class for STL objects that allows them easily to be moved about
by the mouse.  The STL object itself is a Shape3D loaded by the STL loader.

First version 14 April 2006
This version: 14 April 2006
 
 */

package org.reprap.geometry.polyhedra;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import javax.media.j3d.Appearance;
import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Group;
import javax.media.j3d.Material;
import javax.media.j3d.Node;
import javax.media.j3d.SceneGraphObject;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Color3f;
import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.configuration.Constants;
import org.reprap.graphicio.CSGReader;
import org.reprap.graphicio.StlFile;
import org.reprap.gui.MouseObject;

import com.sun.j3d.loaders.IncorrectFormatException;
import com.sun.j3d.loaders.ParsingErrorException;
import com.sun.j3d.loaders.Scene;
import com.sun.j3d.utils.picking.PickTool;

/**
 * Class for holding a group (maybe just 1) of 3D objects for RepRap to make.
 * They can be moved around on the build platform en mass, but not moved
 * relative to each other, so they can represent an assembly made from several
 * different materials.
 * 
 * @author adrian
 */
public class STLObject {
    private static final Logger LOGGER = LogManager.getLogger(STLObject.class);

    private final BranchGroup top = new BranchGroup(); // The thing that links us to the world
    private final BranchGroup handle = new BranchGroup(); // Internal handle for the mouse to grab
    private final TransformGroup trans = new TransformGroup(); // Static transform for when the mouse is away
    private final BranchGroup stl = new BranchGroup(); // The actual STL geometry; a tree duplicated flat in the list contents
    private final List<STLFileContents> contents = new ArrayList<STLFileContents>();
    private MouseObject mouse = null; // The mouse, if it is controlling us
    private Vector3d extent = null; // X, Y and Z extent
    private BoundingBox bbox = null; // Temporary storage for the bounding box while loading
    private Vector3d rootOffset = null; // Offset of the first-loaded STL under stl

    public STLObject() {
        setCommonCapabilities(top, handle, stl, trans);
        top.setCapability(BranchGroup.ALLOW_DETACH);
        top.setCapability(Node.ALLOW_AUTO_COMPUTE_BOUNDS_READ);
        top.setCapability(Node.ALLOW_BOUNDS_READ);
        handle.setCapability(BranchGroup.ALLOW_DETACH);
        stl.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        stl.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        trans.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        trans.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);

        trans.addChild(stl);
        handle.addChild(trans);
        top.addChild(handle);

        final Attributes nullAtt = new Attributes(null, this, null);
        top.setUserData(nullAtt);
        handle.setUserData(nullAtt);
        trans.setUserData(nullAtt);
        stl.setUserData(nullAtt);
    }

    private void setCommonCapabilities(final SceneGraphObject... objects) {
        for (final SceneGraphObject object : objects) {
            object.setCapability(Group.ALLOW_CHILDREN_EXTEND);
            object.setCapability(Group.ALLOW_CHILDREN_WRITE);
            object.setCapability(Group.ALLOW_CHILDREN_READ);
        }
    }

    private static Appearance unselectedApp() {
        final Color3f unselectedColour = new Color3f((float) 0.3, (float) 0.3, (float) 0.3);
        final Appearance unselectedApp = new Appearance();
        unselectedApp.setMaterial(new Material(unselectedColour, Constants.BLACK, unselectedColour, Constants.BLACK, 0f));
        return unselectedApp;
    }

    /**
     * Load an STL object from a file with a known offset (set that null to put
     * the object in the middle of the bed) and set its appearance
     */
    public Attributes addSTL(final File location, final Vector3d offset, final Appearance app, final STLObject lastPicked) {
        final Attributes att;
        if (app == null) {
            att = new Attributes(null, this, unselectedApp());
        } else {
            att = new Attributes(null, this, app);
        }

        try {
            final STLFileContents child = loadSingleSTL(location, att, offset, lastPicked);
            if (child == null) {
                return null;
            }
            if (lastPicked == null) {
                contents.add(child);
            } else {
                lastPicked.contents.add(child);
            }
            return att;
        } catch (final FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (final IncorrectFormatException e) {
            throw new RuntimeException(e);
        } catch (final ParsingErrorException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Actually load the stl file and set its attributes. Offset decides where
     * to put it relative to the origin. If lastPicked is null, the file is
     * loaded as a new independent STLObject; if not it is added to lastPicked
     * and subsequently is subjected to all the same transforms, so they retain
     * their relative positions. This is how multi-material objects are loaded.
     */
    private STLFileContents loadSingleSTL(final File location, final Attributes att, final Vector3d offset,
            final STLObject lastPicked) throws FileNotFoundException, IncorrectFormatException, ParsingErrorException {
        final CSGReader csgr = new CSGReader(location);
        CSG3D csgResult = null;
        if (csgr.csgAvailable()) {
            csgResult = csgr.csg();
        }

        final StlFile loader = new StlFile();
        final Scene scene = loader.load(location.getAbsolutePath());
        if (scene == null) {
            return new STLFileContents(location, null, csgResult, att, 0);
        }

        final BranchGroup bgResult = scene.getSceneGroup();
        bgResult.setCapability(Node.ALLOW_BOUNDS_READ);
        bgResult.setCapability(Group.ALLOW_CHILDREN_READ);
        bgResult.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);

        double volume = 0;
        // Recursively add its attribute
        final Hashtable<?, ?> namedObjects = scene.getNamedObjects();
        for (final Object tt : Collections.list(namedObjects.elements())) {
            if (tt instanceof Shape3D) {
                final Shape3D value = (Shape3D) tt;
                volume += s3dVolume(value);
                bbox = (BoundingBox) value.getBounds();
                value.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
                final GeometryArray g = (GeometryArray) value.getGeometry();
                g.setCapability(GeometryArray.ALLOW_COORDINATE_WRITE);
                recursiveSetUserData(value, att);
            }
        }

        bgResult.setUserData(att);
        if (lastPicked != null) {
            // Add this object to lastPicked
            csgResult = setOffset(bgResult, csgResult, lastPicked.rootOffset);
            lastPicked.stl.addChild(bgResult);
            lastPicked.setAppearance(lastPicked.getAppearance());
            lastPicked.updateBox(bbox);
        } else {
            // New independent object.
            stl.addChild(bgResult);

            Vector3d bottomLeftShift;

            if (bbox != null) {
                final javax.vecmath.Point3d p0 = new javax.vecmath.Point3d();
                final javax.vecmath.Point3d p1 = new javax.vecmath.Point3d();
                bbox.getLower(p0);
                bbox.getUpper(p1);

                if (offset != null) {
                    rootOffset = new Vector3d(offset);
                } else {
                    // If no offset requested, set it to bottom-left-at-origin
                    rootOffset = new Vector3d(-p0.x, -p0.y, -p0.z);
                }

                // How big?
                extent = new Vector3d(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z);
                final Vector3d centre = scale(extent, 0.5);

                // Position us centre at origin:
                rootOffset = add(rootOffset, neg(centre));
                bottomLeftShift = centre;
            } else {
                bottomLeftShift = null;
                rootOffset = null;
                LOGGER.error("applyOffset(): no bounding box or child.");
            }

            csgResult = setOffset(stl, csgResult, rootOffset);
            final Transform3D temp_t = new Transform3D();
            temp_t.set(bottomLeftShift);
            trans.setTransform(temp_t);
            restoreAppearance();
        }

        return new STLFileContents(location, bgResult, csgResult, att, volume);
    }

    private void updateBox(final BoundingBox bb) {
        final javax.vecmath.Point3d pNew = new javax.vecmath.Point3d();
        final javax.vecmath.Point3d pOld = new javax.vecmath.Point3d();
        bb.getLower(pNew);
        bbox.getLower(pOld);
        if (pNew.x < pOld.x) {
            pOld.x = pNew.x;
        }
        if (pNew.y < pOld.y) {
            pOld.y = pNew.y;
        }
        if (pNew.z < pOld.z) {
            pOld.z = pNew.z;
        }
        bbox.setLower(pOld);
        extent = new Vector3d();
        extent.x = pOld.x;
        extent.y = pOld.y;
        extent.z = pOld.z;

        bb.getUpper(pNew);
        bbox.getUpper(pOld);
        if (pNew.x > pOld.x) {
            pOld.x = pNew.x;
        }
        if (pNew.y > pOld.y) {
            pOld.y = pNew.y;
        }
        if (pNew.z > pOld.z) {
            pOld.z = pNew.z;
        }
        bbox.setUpper(pOld);

        extent.x = pOld.x - extent.x;
        extent.y = pOld.y - extent.y;
        extent.z = pOld.z - extent.z;

    }

    public BranchGroup top() {
        return top;
    }

    public TransformGroup trans() {
        return trans;
    }

    public BranchGroup handle() {
        return handle;
    }

    public Vector3d extent() {
        return new Vector3d(extent);
    }

    public File fileAndDirectioryItCameFrom(final int i) {
        return contents.get(i).getSourceFile();
    }

    public String fileItCameFrom(final int i) {
        return fileAndDirectioryItCameFrom(i).getName();
    }

    String toSCAD() {
        String result = " multmatrix(m = [ [";
        final Transform3D t1 = new Transform3D();
        final Transform3D t2 = new Transform3D();
        trans.getTransform(t1);
        t2.set(1.0, rootOffset);
        t1.mul(t2);
        final Matrix4d m = new Matrix4d();
        t1.get(m);
        result += new BigDecimal(Double.toString(m.m00)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m01)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m02)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m03)).toPlainString() + "], \n   [";

        result += new BigDecimal(Double.toString(m.m10)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m11)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m12)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m13)).toPlainString() + "], \n   [";

        result += new BigDecimal(Double.toString(m.m20)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m21)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m22)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m23)).toPlainString() + "], \n   [";

        result += new BigDecimal(Double.toString(m.m30)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m31)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m32)).toPlainString() + ", ";
        result += new BigDecimal(Double.toString(m.m33)).toPlainString() + "]]) \n   {\n";

        for (int i = 0; i < contents.size(); i++) {
            result += "      import(\"";
            result += fileItCameFrom(i) + "\", convexity = 10);\n";
        }
        result += "   }\n";

        return result;
    }

    public Attributes attributes(final int i) {
        return contents.get(i).getAtt();
    }

    public int size() {
        return contents.size();
    }

    public void setUnique(final int i, final int v) {
        contents.get(i).setUnique(v);
    }

    public int getUnique(final int i) {
        return contents.get(i).getUnique();
    }

    /**
     * Make an STL object from an existing BranchGroup
     */
    public STLObject(final BranchGroup s) {
        this();

        stl.addChild(s);
        extent = new Vector3d(1, 1, 1); // Should never be needed.

        final Transform3D temp_t = new Transform3D();
        trans.setTransform(temp_t);
    }

    /**
     * method to recursively set the user data for objects in the scenegraph
     * tree we also set the capabilites on Shape3D objects required by the
     * PickTool
     * 
     * @param value
     * @param me
     */
    private void recursiveSetUserData(final Object value, final Object me) {
        if (value instanceof SceneGraphObject) {
            // set the user data for the item
            final SceneGraphObject sg = (SceneGraphObject) value;
            sg.setUserData(me);

            // recursively process group
            if (sg instanceof Group) {
                final Group g = (Group) sg;

                // recurse on child nodes
                final java.util.Enumeration<?> enumKids = g.getAllChildren();

                while (enumKids.hasMoreElements()) {
                    recursiveSetUserData(enumKids.nextElement(), me);
                }
            } else if (sg instanceof Shape3D) {
                ((Shape3D) sg).setUserData(me);
                PickTool.setCapabilities((Node) sg, PickTool.INTERSECT_FULL);
            }
        }
    }

    // Move the object by p permanently (i.e. don't just apply a transform).

    private void recursiveSetOffset(final Object value, final Vector3d p) {
        if (value instanceof SceneGraphObject != false) {
            // set the user data for the item
            final SceneGraphObject sg = (SceneGraphObject) value;

            // recursively process group
            if (sg instanceof Group) {
                final Group g = (Group) sg;

                // recurse on child nodes
                final java.util.Enumeration<?> enumKids = g.getAllChildren();

                while (enumKids.hasMoreElements()) {
                    recursiveSetOffset(enumKids.nextElement(), p);
                }
            } else if (sg instanceof Shape3D) {
                s3dOffset((Shape3D) sg, p);
            }
        }
    }

    private CSG3D setOffset(final BranchGroup bg, final CSG3D c, final Vector3d p) {
        recursiveSetOffset(bg, p);
        if (c == null) {
            return null;
        }
        final Matrix4d m = new Matrix4d();
        m.setIdentity();
        m.m03 = p.x;
        m.m13 = p.y;
        m.m23 = p.z;
        return c.transform(m);
    }

    /**
     * Soft translation
     */
    public void translate(final Vector3d p) {
        final Transform3D t3d1 = getTransform();
        final Transform3D t3d2 = new Transform3D();
        t3d2.set(p);
        t3d1.mul(t3d2);
        setTransform(t3d1);
    }

    // Shift a Shape3D permanently by p
    private void s3dOffset(final Shape3D shape, final Tuple3d p) {
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d p3d = new Point3d();
        if (g != null) {
            for (int i = 0; i < g.getVertexCount(); i++) {
                g.getCoordinate(i, p3d);
                p3d.add(p);
                g.setCoordinate(i, p3d);
            }
        }
    }

    // Scale the object by s permanently (i.e. don't just apply a transform).
    private void recursiveSetScale(final Object value, final double s, final boolean zOnly) {
        if (value instanceof SceneGraphObject != false) {
            // set the user data for the item
            final SceneGraphObject sg = (SceneGraphObject) value;

            // recursively process group
            if (sg instanceof Group) {
                final Group g = (Group) sg;

                // recurse on child nodes
                final java.util.Enumeration<?> enumKids = g.getAllChildren();

                while (enumKids.hasMoreElements()) {
                    recursiveSetScale(enumKids.nextElement(), s, zOnly);
                }
            } else if (sg instanceof Shape3D) {
                s3dScale((Shape3D) sg, s, zOnly);
            }
        }
    }

    // Scale a Shape3D permanently by s

    private void s3dScale(final Shape3D shape, final double s, final boolean zOnly) {
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d p3d = new Point3d();
        if (g != null) {
            for (int i = 0; i < g.getVertexCount(); i++) {
                g.getCoordinate(i, p3d);
                if (zOnly) {
                    p3d.z = s * p3d.z;
                } else {
                    p3d.scale(s);
                }
                g.setCoordinate(i, p3d);
            }
        }
    }

    // Set my transform
    public void setTransform(final Transform3D t3d) {
        trans.setTransform(t3d);
    }

    // Get my transform
    public Transform3D getTransform() {
        final Transform3D result = new Transform3D();
        trans.getTransform(result);
        return result;
    }

    // Get one of the the actual objects
    public BranchGroup getSTL() {
        return stl;
    }

    public BranchGroup getSTL(final int i) {
        return contents.get(i).getStl();
    }

    public int getCount() {
        return contents.size();
    }

    public CSG3D getCSG(final int i) {
        return contents.get(i).getCsg();
    }

    // Get the number of objects
    public int numChildren() {
        return stl.numChildren();
    }

    // The mouse calls this to tell us it is controlling us
    public void setMouse(final MouseObject m) {
        mouse = m;
    }

    // Change colour etc. - recursive private call to walk the tree
    private static void setAppearance_r(final Object gp, final Appearance a) {
        if (gp instanceof Group) {
            final Group g = (Group) gp;

            // recurse on child nodes
            final java.util.Enumeration<?> enumKids = g.getAllChildren();

            while (enumKids.hasMoreElements()) {
                final Object child = enumKids.nextElement();
                if (child instanceof Shape3D) {
                    final Shape3D lf = (Shape3D) child;
                    lf.setAppearance(a);
                } else {
                    setAppearance_r(child, a);
                }
            }
        }
    }

    // Change colour etc. - call the internal fn to do the work.
    public void setAppearance(final Appearance a) {
        setAppearance_r(stl, a);
    }

    /**
     * dig down to find our appearance
     * 
     * @param gp
     * @return
     */
    private static Appearance getAppearance_r(final Object gp) {
        if (gp instanceof Group) {
            final Group g = (Group) gp;

            // recurse on child nodes
            final java.util.Enumeration<?> enumKids = g.getAllChildren();

            while (enumKids.hasMoreElements()) {
                final Object child = enumKids.nextElement();
                if (child instanceof Shape3D) {
                    final Shape3D lf = (Shape3D) child;
                    return lf.getAppearance();
                } else {
                    return getAppearance_r(child);
                }
            }
        }
        return new Appearance();
    }

    public Appearance getAppearance() {
        return getAppearance_r(stl);
    }

    /**
     * Restore the appearances to the correct colour.
     */
    public void restoreAppearance() {
        final java.util.Enumeration<?> enumKids = stl.getAllChildren();

        while (enumKids.hasMoreElements()) {
            final Object b = enumKids.nextElement();
            if (b instanceof BranchGroup) {
                final Attributes att = (Attributes) ((BranchGroup) b).getUserData();
                if (att != null) {
                    setAppearance_r(b, att.getAppearance());
                } else {
                    LOGGER.error("restoreAppearance(): no Attributes!");
                }
            }
        }
    }

    // Why the !*$! aren't these in Vector3d???
    private static Vector3d add(final Vector3d a, final Vector3d b) {
        final Vector3d result = new Vector3d();
        result.x = a.x + b.x;
        result.y = a.y + b.y;
        result.z = a.z + b.z;
        return result;
    }

    private static Vector3d neg(final Vector3d a) {
        final Vector3d result = new Vector3d(a);
        result.negate();
        return result;
    }

    private static Vector3d scale(final Vector3d a, final double s) {
        final Vector3d result = new Vector3d(a);
        result.scale(s);
        return result;
    }

    // Put a vector in the positive octant (sort of abs for vectors)
    private Vector3d posOct(final Vector3d v) {
        final Vector3d result = new Vector3d();
        result.x = Math.abs(v.x);
        result.y = Math.abs(v.y);
        result.z = Math.abs(v.z);
        return result;
    }

    // Apply a rotating click transform about one of the coordinate axes,
    // which should be set in t.  This can only be done if we're being controlled
    // by the mouse, making us the active object.
    private void rClick(final Transform3D t) {
        if (mouse == null) {
            return;
        }

        // Get the mouse transform and split it into a rotation and a translation
        final Transform3D mtrans = new Transform3D();
        mouse.getTransform(mtrans);
        Vector3d mouseTranslation = new Vector3d();
        final Matrix3d mouseRotation = new Matrix3d();
        mtrans.get(mouseRotation, mouseTranslation);

        // Subtract the part of the translation that puts the bottom left corner
        // at the origin.
        Vector3d zero = scale(extent, 0.5);
        mouseTranslation = add(mouseTranslation, neg(zero));

        // Click the size record round by t
        t.transform(extent);
        extent = posOct(extent);

        // Apply the new rotation to the existing one
        final Transform3D spin = new Transform3D();
        spin.setRotation(mouseRotation);
        t.mul(spin);

        // Add a new translation to put the bottom left corner
        // back at the origin.
        zero = scale(extent, 0.5);
        mouseTranslation = add(mouseTranslation, zero);

        // Then slide us back where we were
        final Transform3D fromZeroT = new Transform3D();
        fromZeroT.setTranslation(mouseTranslation);

        fromZeroT.mul(t);

        // Apply the whole new transformation
        mouse.setTransform(fromZeroT);
    }

    /**
     * Rescale the STL object (for inch -> mm conversion) and stretching heights
     */
    public void rScale(final double s, final boolean zOnly) {
        if (mouse == null && !zOnly) {
            return;
        }
        Vector3d mouseTranslation = null;
        Matrix3d mouseRotation = null;

        // Get the mouse transform and split it into a rotation and a translation
        final Transform3D mtrans = new Transform3D();
        if (mouse != null) {
            mouse.getTransform(mtrans);
            mouseTranslation = new Vector3d();
            mouseRotation = new Matrix3d();
            mtrans.get(mouseRotation, mouseTranslation);
        }

        // Subtract the part of the translation that puts the bottom left corner
        // at the origin.
        Vector3d zero = scale(extent, 0.5);

        if (mouse != null) {
            mouseTranslation = add(mouseTranslation, neg(zero));
        }

        // Rescale the box
        if (zOnly) {
            extent.z = s * extent.z;
        } else {
            extent.scale(s);
        }

        // Add a new translation to put the bottom left corner
        // back at the origin.
        zero = scale(extent, 0.5);

        if (mouse != null) {
            mouseTranslation = add(mouseTranslation, zero);

            // Then slide us back where we were
            final Transform3D fromZeroT = new Transform3D();
            fromZeroT.setTranslation(mouseTranslation);

            // Apply the whole new transformation
            mouse.setTransform(fromZeroT);
        }

        Enumeration<?> things;

        things = stl.getAllChildren();
        while (things.hasMoreElements()) {
            final Object value = things.nextElement();
            recursiveSetScale(value, s, zOnly);
        }

    }

    public void xClick() {
        if (mouse == null) {
            return;
        }

        final Transform3D x90 = new Transform3D();
        x90.set(new AxisAngle4d(1, 0, 0, 0.5 * Math.PI));

        rClick(x90);
    }

    public void yClick() {
        if (mouse == null) {
            return;
        }

        final Transform3D y90 = new Transform3D();
        y90.set(new AxisAngle4d(0, 1, 0, 0.5 * Math.PI));

        rClick(y90);
    }

    public void zClick(final double angle) {
        if (mouse == null) {
            return;
        }

        final Transform3D zAngle = new Transform3D();
        zAngle.set(new AxisAngle4d(0, 0, 1, angle * Math.PI / 180.0));

        rClick(zAngle);
    }

    /**
     * Converts the object from inches to mm.
     */
    public void inToMM() {
        if (mouse == null) {
            return;
        }
        rScale(Constants.INCH_TO_MM, false);
    }

    /**
     * Return the volume of the last-added item
     */
    public double volume() {
        if (contents == null) {
            return 0;
        }
        if (contents.size() <= 0) {
            return 0;
        }
        return contents.get(contents.size() - 1).getVolume();
    }

    /**
     * Compute the volume of a Shape3D
     */
    private double s3dVolume(final Shape3D shape) {
        double total = 0;
        final GeometryArray g = (GeometryArray) shape.getGeometry();
        final Point3d a = new Point3d();
        final Point3d b = new Point3d();
        final Point3d c = new Point3d();
        if (g != null) {
            for (int i = 0; i < g.getVertexCount(); i += 3) {
                g.getCoordinate(i, a);
                g.getCoordinate(i + 1, b);
                g.getCoordinate(i + 2, c);
                total += prismVolume(a, b, c);
            }
        }
        return Math.abs(total);
    }

    /**
     * Compute the signed volume of a tetrahedron
     */
    private double tetVolume(final Point3d a, final Point3d b, final Point3d c, final Point3d d) {
        final Matrix3d m = new Matrix3d(b.x - a.x, c.x - a.x, d.x - a.x, b.y - a.y, c.y - a.y, d.y - a.y, b.z - a.z, c.z - a.z,
                d.z - a.z);
        return m.determinant() / 6.0;
    }

    /**
     * Compute the signed volume of the prism between the XY plane and the space
     * triangle {a, b, c}
     */
    private double prismVolume(final Point3d a, final Point3d b, final Point3d c) {
        final Point3d d = new Point3d(a.x, a.y, 0);
        final Point3d e = new Point3d(b.x, b.y, 0);
        final Point3d f = new Point3d(c.x, c.y, 0);
        return tetVolume(a, b, c, e) + tetVolume(a, e, c, d) + tetVolume(e, f, c, d);
    }
}
