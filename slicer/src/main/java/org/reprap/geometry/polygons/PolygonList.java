/*
 
 RepRap
 ------
 
 The Replicating Rapid Prototyper Project
 
 
 Copyright (C) 2005
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
 
 
 RrPolygonList: A collection of 2D polygons
 
 First version 20 May 2005
 This version: 1 May 2006 (Now in CVS - no more comments here)
 
 */

package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * RrPolygonList: A collection of 2D polygons List of polygons class. This too
 * maintains a maximum enclosing rectangle.
 */
public class PolygonList {
    private static final Logger LOGGER = LogManager.getLogger(PolygonList.class);
    private final List<Polygon> polygons = new ArrayList<Polygon>();
    private final Rectangle box = new Rectangle();

    public PolygonList() {
    }

    /**
     * Deep copy
     * 
     * @param lst
     *            list of polygons to copy
     */
    public PolygonList(final PolygonList lst) {
        box.expand(lst.box);
        for (int i = 0; i < lst.size(); i++) {
            polygons.add(new Polygon(lst.polygon(i)));
        }
    }

    /**
     * Get the data
     * 
     * @param i
     *            index of polygon to return
     * @return polygon at index i
     */
    public Polygon polygon(final int i) {
        return polygons.get(i);
    }

    /**
     * @return number of polygons in the list
     */
    public int size() {
        return polygons.size();
    }

    /**
     * Overwrite one of the polygons
     * 
     * @param i
     *            index of polygon to overwrite
     * @param p
     *            polygon to set at index i
     */
    public void set(final int i, final Polygon p) {
        polygons.set(i, p);
    }

    /**
     * Remove one from the list
     * 
     * @param i
     *            index of polygon to remove
     */
    private void remove(final int i) {
        polygons.remove(i);
    }

    /**
     * Put a new list on the end
     * 
     * @param lst
     *            list to append to existing polygon list
     */
    public void add(final PolygonList lst) {
        for (int i = 0; i < lst.size(); i++) {
            polygons.add(new Polygon(lst.polygon(i)));
        }
        box.expand(lst.box);
    }

    /**
     * Add one new polygon to the list
     * 
     * @param p
     *            polygon to add to the list
     */
    public void add(final Polygon p) {
        polygons.add(p);
        box.expand(p.getBox());
    }

    /**
     * Swap two in the list
     * 
     * @param i
     * @param j
     */
    private void swap(final int i, final int j) {
        final Polygon p = polygons.get(i);
        polygons.set(i, polygons.get(j));
        polygons.set(j, p);
    }

    /**
     * Negate one of the polygons
     * 
     * @param i
     */
    private void negate(final int i) {
        final Polygon p = polygon(i).negate();
        polygons.set(i, p);
    }

    @Override
    public String toString() {
        String result = "Polygon List - polygons: ";
        result += size() + ", enclosing box: ";
        result += box.toString();
        for (int i = 0; i < size(); i++) {
            result += "\n" + polygon(i).toString();
        }
        return result;
    }

    /**
     * Simplify all polygons by length d N.B. this may throw away small ones
     * completely
     * 
     * @return simplified polygon list
     */
    public PolygonList simplify(final double d) {
        final PolygonList r = new PolygonList();
        final double d2 = d * d;

        for (int i = 0; i < size(); i++) {
            final Polygon p = polygon(i);
            if (p.getBox().dSquared() > 2 * d2) {
                r.add(p.simplify(d));
            }
        }

        return r;
    }

    /**
     * Re-order and (if need be) reverse the order of the polygons in a list so
     * the end of the first is near the start of the second and so on. This is a
     * heuristic - it does not do a full travelling salesman... This deals with
     * both open and closed polygons.
     */
    public PolygonList nearEnds(final Point2D startNearHere) {
        final PolygonList result = new PolygonList();
        if (size() <= 0) {
            return result;
        }

        for (int i = 0; i < size(); i++) {
            result.add(polygon(i));
        }

        // Make the nearest end point on any polygon to startNearHere
        // go to polygon 0 and get it the right way round if it's open.

        // Begin by moving the polygon nearest the specified start point to the head
        // of the list.
        if (startNearHere != null) {
            double d = Double.POSITIVE_INFINITY;
            boolean neg = false;
            int near = -1;
            for (int i = 0; i < size(); i++) {
                double d2 = Point2D.dSquared(startNearHere, result.polygon(i).point(0));
                if (d2 < d) {
                    near = i;
                    d = d2;
                    neg = false;
                }
                if (!result.polygon(i).isClosed()) {
                    d2 = Point2D.dSquared(startNearHere, result.polygon(i).point(result.polygon(i).size() - 1));
                    if (d2 < d) {
                        near = i;
                        d = d2;
                        neg = true;
                    }
                }
            }

            if (near < 0) {
                throw new RuntimeException("RrPolygonList.nearEnds(): no nearest end found to start point!");
            }

            result.swap(0, near);
            if (neg) {
                result.negate(0);
            }
        }

        // Go through the rest of the polygons getting them as close as
        // reasonable.
        for (int i = 0; i < result.size() - 1; i++) {
            final Point2D end;
            if (result.polygon(i).isClosed()) {
                end = result.polygon(i).point(0);
            } else {
                end = result.polygon(i).point(result.polygon(i).size() - 1);
            }
            boolean neg = false;
            int near = -1;
            double d = Double.POSITIVE_INFINITY;
            for (int j = i + 1; j < result.size(); j++) {
                double d2 = Point2D.dSquared(end, result.polygon(j).point(0));
                if (d2 < d) {
                    near = j;
                    d = d2;
                    neg = false;
                }

                if (!result.polygon(j).isClosed()) {
                    d2 = Point2D.dSquared(end, result.polygon(j).point(result.polygon(j).size() - 1));
                    if (d2 < d) {
                        near = j;
                        d = d2;
                        neg = true;
                    }
                }
            }

            if (near > 0) {
                if (neg) {
                    result.negate(near);
                }
                result.swap(i + 1, near);
            }
        }

        return result;
    }

    /**
     * Take all the polygons in a list, both open and closed, and reorder them
     * such that accessible points on any that have a squared distance less than
     * linkUp to accessible points on any others are joined to form single
     * polygons.
     * 
     * For an open polygon the accessible points are just its ends. For a closed
     * polygon all its points are accessible.
     * 
     * This is a fairly radical remove in-air movement strategy.
     * 
     * All the polygons in the list must be plotted with the same physical
     * extruder (otherwise it would be nonsense to join them). It is the calling
     * function's responsibility to make sure this is the case.
     */
    public void radicalReOrder(final double linkUp) {
        if (size() < 2) {
            return;
        }

        // First check that we all have the same material
        validateSameMaterial();

        // Now go through the polygons pairwise
        for (int i = 0; i < size() - 1; i++) {
            for (int j = i + 1; j < size(); j++) {
                // Swap the odd half of the asymmetric cases so they're all the same
                if (polygon(i).isClosed() && !polygon(j).isClosed()) {
                    final Polygon left = polygon(i);
                    final Polygon right = polygon(j);
                    polygons.set(i, right);
                    polygons.set(j, left);
                }

                final boolean leftClosed = polygon(i).isClosed();
                final boolean rightClosed = polygon(j).isClosed();
                final boolean joined;
                // Three possibilities ...
                if (!leftClosed && !rightClosed) {
                    joined = handleTwoOpenPolygons(linkUp, i, j);
                } else if (!leftClosed && rightClosed) {
                    joined = handleOpenAndClosedPolygon(linkUp, i, j);
                } else if (leftClosed && rightClosed) {
                    joined = handleTwoClosedPolygons(linkUp, i, j);
                } else {
                    throw new RuntimeException("RrPolygonList.radicalReOrder(): Polygons are neither closed nor open!");
                }
                if (joined) {
                    polygons.remove(j);
                    // we joined two polygons, so retry with the same index
                    j--;
                }
            }
        }
    }

    private boolean handleTwoClosedPolygons(final double linkUp, final int i, final int j) {
        Polygon myPolygon = polygon(i);
        Polygon itsPolygon = polygon(j);
        int myPoint = -1;
        int itsPoint = -1;
        double d = Double.POSITIVE_INFINITY;
        for (int k = 0; k < itsPolygon.size(); k++) {
            final int myTempPoint = myPolygon.nearestVertex(itsPolygon.point(k));
            final double d2 = Point2D.dSquared(myPolygon.point(myTempPoint), itsPolygon.point(k));
            if (d2 < d) {
                myPoint = myTempPoint;
                itsPoint = k;
                d = d2;
            }
        }

        if (d < linkUp) {
            myPolygon = myPolygon.newStart(myPoint);
            myPolygon.add(firstPointOf(myPolygon)); // Make sure we come back to the start
            itsPolygon = itsPolygon.newStart(itsPoint);
            itsPolygon.add(firstPointOf(itsPolygon)); // Make sure we come back to the start
            myPolygon.add(itsPolygon);
            polygons.set(i, myPolygon);
            return true;
        }
        return false;
    }

    private boolean handleOpenAndClosedPolygon(final double linkUp, final int i, final int j) {
        Polygon myPolygon = polygon(i);
        Polygon itsPolygon = polygon(j);
        int itsPoint = itsPolygon.nearestVertex(firstPointOf(myPolygon));
        double d = Point2D.dSquared(itsPolygon.point(itsPoint), firstPointOf(myPolygon));
        final int itsTempPoint = itsPolygon.nearestVertex(lastPointOf(myPolygon));
        final double d2 = Point2D.dSquared(itsPolygon.point(itsTempPoint), lastPointOf(myPolygon));
        boolean reverseMe = true;
        if (d2 < d) {
            itsPoint = itsTempPoint;
            reverseMe = false;
            d = d2;
        }

        if (d < linkUp) {
            itsPolygon = itsPolygon.newStart(itsPoint);
            itsPolygon.add(firstPointOf(itsPolygon)); // Make sure the second half really is closed
            if (reverseMe) {
                myPolygon = myPolygon.negate();
            }
            myPolygon.add(itsPolygon);
            myPolygon.setOpen(); // We were closed, but we must now be open
            polygons.set(i, myPolygon);
            return true;
        }
        return false;
    }

    private static Point2D firstPointOf(final Polygon polygon) {
        return polygon.point(0);
    }

    private static Point2D lastPointOf(final Polygon polygon) {
        return polygon.point(polygon.size() - 1);
    }

    private boolean handleTwoOpenPolygons(final double linkUp, final int i, final int j) {
        Polygon myPolygon = polygon(i);
        Polygon itsPolygon = polygon(j);
        boolean reverseMe = true;
        boolean reverseIt = false;
        double d = Point2D.dSquared(firstPointOf(myPolygon), firstPointOf(itsPolygon));
        double d2 = Point2D.dSquared(lastPointOf(myPolygon), firstPointOf(itsPolygon));
        if (d2 < d) {
            reverseMe = false;
            reverseIt = false;
            d = d2;
        }

        d2 = Point2D.dSquared(firstPointOf(myPolygon), lastPointOf(itsPolygon));
        if (d2 < d) {
            reverseMe = true;
            reverseIt = true;
            d = d2;
        }

        d2 = Point2D.dSquared(lastPointOf(myPolygon), lastPointOf(itsPolygon));
        if (d2 < d) {
            reverseMe = false;
            reverseIt = true;
            d = d2;
        }

        if (d < linkUp) {
            if (reverseMe) {
                myPolygon = myPolygon.negate();
            }
            if (reverseIt) {
                itsPolygon = itsPolygon.negate();
            }
            myPolygon.add(itsPolygon);
            polygons.set(i, myPolygon);
            return true;
        }
        return false;
    }

    private void validateSameMaterial() {
        for (int i = 1; i < size(); i++) {
            if (!polygon(0).getMaterial().equals(polygon(i).getMaterial())) {
                throw new RuntimeException("RrPolygonList.radicalReOrder(): more than one material in the list!");
            }
        }
    }

    /**
     * Remove polygon pol from the list, replacing it with two polygons, the
     * first being pol's vertices from 0 to st inclusive, and the second being
     * pol's vertices from en to its end inclusive. It is permissible for st ==
     * en, but if st > en, then they are swapped.
     * 
     * The two new polygons are put on the end of the list.
     */
    public void cutPolygon(final int pol, int st, int en) {
        final Polygon old = polygon(pol);
        final Polygon p1 = new Polygon(old.getMaterial(), old.isClosed());
        final Polygon p2 = new Polygon(old.getMaterial(), old.isClosed());
        if (st > en) {
            final int temp = st;
            st = en;
            en = temp;
        }
        if (st > 0) {
            for (int i = 0; i <= st; i++) {
                p1.add(old.point(i));
            }
        }
        if (en < old.size() - 1) {
            for (int i = en; i < old.size(); i++) {
                p2.add(old.point(i));
            }
        }
        remove(pol);
        if (p1.size() > 1) {
            add(p1);
        }
        if (p2.size() > 1) {
            add(p2);
        }
    }

    /**
     * Search a polygon list to find the nearest point on all the polygons
     * within it to the point p.
     * 
     * Only polygons with the same material are compared.
     */
    public PolygonIndexedPoint ppSearch(final Point2D p, final String material) {
        if (size() <= 0) {
            return null;
        }
        double minDistance = Double.POSITIVE_INFINITY;
        PolygonIndexedPoint result = null;
        for (int i = 0; i < size(); i++) {
            final Polygon pgon = polygon(i);
            if (material.equals(pgon.getMaterial())) {
                final int n = pgon.nearestVertex(p);
                final double distance = Point2D.dSquared(p, pgon.point(n));
                if (distance < minDistance) {
                    result = new PolygonIndexedPoint(n, i, pgon);
                    minDistance = distance;
                }
            }
        }
        if (result == null) {
            throw new RuntimeException("no point found!");
        }
        return result;
    }

    /**
     * Is polygon i inside CSG polygon j? (Check twice to make sure...)
     * 
     * @return true if the polygon is inside the CSG polygon, false if otherwise
     */
    private boolean inside(final int i, final int j, final List<CSG2D> csgPols) {
        // FIXME: check twice??? What.
        final CSG2D exp = csgPols.get(j);
        Point2D p = polygon(i).point(0);
        final boolean a = (exp.value(p) <= 0);
        p = polygon(i).point(polygon(i).size() / 2);
        final boolean b = (exp.value(p) <= 0);
        if (a != b) {
            LOGGER.error("i is both inside and outside j!");
            // casting vote...
            p = polygon(i).point(polygon(i).size() / 3);
            return exp.value(p) <= 0;
        }
        return a;
    }

    /**
     * Take a list of CSG expressions, each one corresponding with the entry of
     * the same index in this class, classify each as being inside other(s) (or
     * not), and hence form a single CSG expression representing them all.
     * 
     * @return single CSG expression based on csgPols list
     */
    private CSG2D resolveInsides(final List<CSG2D> csgPols) {
        int i, j;

        final TreeList universe = new TreeList(-1);
        universe.addChild(new TreeList(0));

        // For each polygon construct a list of all the others that
        // are inside it (if any).
        for (i = 0; i < size() - 1; i++) {
            TreeList isList = universe.walkFind(i);
            if (isList == null) {
                isList = new TreeList(i);
                universe.addChild(isList);
            }
            for (j = i + 1; j < size(); j++) {
                TreeList jsList = universe.walkFind(j);
                if (jsList == null) {
                    jsList = new TreeList(j);
                    universe.addChild(jsList);
                }
                if (inside(j, i, csgPols)) {
                    isList.addChild(jsList);
                }
                if (inside(i, j, csgPols)) {
                    jsList.addChild(isList);
                }
            }
        }

        // Set all the parent pointers
        universe.setParents();
        // Eliminate each leaf from every part of the tree except the node immediately above itself
        for (i = 0; i < size(); i++) {
            final TreeList isList = universe.walkFind(i);
            if (isList == null) {
                throw new RuntimeException("RrPolygonList.resolveInsides() - can't find list for polygon " + i);
            }
            TreeList parent = isList.getParent();
            if (parent != null) {
                parent = parent.getParent();
                while (parent != null) {
                    parent.remove(isList);
                    parent = parent.getParent();
                }
            }
        }
        return universe.buildCSG(csgPols);
    }

    /**
     * Compute the CSG representation of all the polygons in the list
     * 
     * @return CSG representation
     */
    public CSG2D toCSG() {
        if (size() == 0) {
            return CSG2D.nothing();
        }
        if (size() == 1) {
            return polygon(0).toCSG();
        }

        final List<CSG2D> csgPols = new ArrayList<CSG2D>();
        for (int i = 0; i < size(); i++) {
            csgPols.add(polygon(i).toCSG());
        }

        return resolveInsides(csgPols);
    }
}
