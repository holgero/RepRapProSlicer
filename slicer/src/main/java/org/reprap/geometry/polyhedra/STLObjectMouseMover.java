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
 
Wrapper class for the mouse-driven transforms.  This makes it easier
To attach the mouse to different objects in the scene.
 
First version 14 April 2006
This version: 14 April 2006
 
 */

package org.reprap.geometry.polyhedra;

import javax.media.j3d.Bounds;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Group;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;

import com.sun.j3d.utils.behaviors.mouse.MouseRotate;
import com.sun.j3d.utils.behaviors.mouse.MouseTranslate;
import com.sun.j3d.utils.behaviors.mouse.MouseZoom;

public class STLObjectMouseMover {
    private static final double MOUSE_TRANSLATION_FACTOR = 50;
    private static final double MOUSE_ZOOM_FACTOR = 50;
    private final BranchGroup top; // Attach this to the rest of the tree
    private final TransformGroup free; // Mouse transform with no restrictions
    private final TransformGroup slide; // Mouse transform that only does XY sliding
    private TransformGroup trans; // Set to one of the two above   
    private STLObject movingThing = null; // The part of the scene being moved

    public STLObjectMouseMover(final Bounds behaviorBounds) {
        // Set up the free transform that allows all movements

        free = new TransformGroup();
        free.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        free.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        final MouseRotate mr = new MouseRotate(free);
        mr.setSchedulingBounds(behaviorBounds);
        free.addChild(mr);

        final MouseTranslate mt = new MouseTranslate(free);
        mt.setSchedulingBounds(behaviorBounds);
        mt.setFactor(MOUSE_TRANSLATION_FACTOR * mt.getXFactor(), MOUSE_TRANSLATION_FACTOR * mt.getYFactor());
        free.addChild(mt);

        final MouseZoom mz = new MouseZoom(free);
        mz.setSchedulingBounds(behaviorBounds);
        mz.setFactor(MOUSE_ZOOM_FACTOR * mz.getFactor());
        free.addChild(mz);

        // Set up the slide transform that only allows XY movement

        slide = new TransformGroup();
        slide.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        slide.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        final MouseTranslate mts = new MouseTranslate(slide);
        mts.setSchedulingBounds(behaviorBounds);
        mts.setFactor(MOUSE_TRANSLATION_FACTOR * mts.getXFactor(), MOUSE_TRANSLATION_FACTOR * mts.getYFactor());
        slide.addChild(mts);

        // Set up the thing to attach and detach
        top = new BranchGroup();

        // ALLOW_EVERYTHING would be useful...
        top.setCapability(BranchGroup.ALLOW_DETACH);
        top.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        top.setCapability(Group.ALLOW_CHILDREN_WRITE);

        free.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        free.setCapability(Group.ALLOW_CHILDREN_WRITE);
        free.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        free.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);

        slide.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        slide.setCapability(Group.ALLOW_CHILDREN_WRITE);
        slide.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        slide.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);

        // Set the default action as unrestricted movement
        top.addChild(free);
        trans = free;
    }

    // Select the STL object to control with the mouse; the boolean
    // decides whether to use free or slide.
    public void move(final STLObject stl, final boolean slideOnly) {
        Transform3D t3d = new Transform3D();
        trans.getTransform(t3d);

        // Detach us from whatever we were stuck to
        top.detach();

        // Take the thing we were moving, set its transform to a fixed version
        // of what we are now, and tell it we aren't controlling it any more.
        if (movingThing != null) {
            movingThing.setTransform(t3d);
            movingThing.handle().detach();
            movingThing.top().addChild(movingThing.handle());
            movingThing.setMouse(null);
        }

        // Record what we will be moving from now on.
        movingThing = stl;

        // Remove the current mouse transform from top...
        top.removeChild(0);

        // ... and add the one selected by slideOnly.
        if (slideOnly) {
            trans = slide;
        } else {
            trans = free;
        }

        top.addChild(trans);

        // Get the current transform of the thing being moved...
        t3d = new Transform3D();
        movingThing.trans().getTransform(t3d);

        // ...set that thing's static transform to the identity...
        final Transform3D identity = new Transform3D();
        movingThing.setTransform(identity);

        // ...and set the mouse transform to that of the thing being moved.
        trans.setTransform(t3d);

        // Put us in the path to the thing being moved.
        movingThing.handle().detach();
        trans.addChild(movingThing.handle());
        movingThing.top().addChild(top);

        top.setUserData(movingThing);
        trans.setUserData(movingThing);

        // Tell it we've taken over.
        movingThing.setMouse(this);
    }

    public void getTransform(final Transform3D t3d) {
        trans.getTransform(t3d);
    }

    public void setTransform(final Transform3D t3d) {
        trans.setTransform(t3d);
    }
}
