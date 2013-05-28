package org.reprap.geometry.grids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.reprap.geometry.polygons.Point2D;
import org.reprap.geometry.polygons.PolygonList;

/**
 * Class to hold a list of BooleanGrids with associated attributes for each
 * 
 * @author ensab
 */
public class BooleanGridList implements Iterable<BooleanGrid> {

    private final List<BooleanGrid> shapes = new ArrayList<BooleanGrid>();

    public BooleanGridList() {
    }

    /**
     * Return the ith shape
     */
    public BooleanGrid get(final int i) {
        return shapes.get(i);
    }

    /**
     * Is a point in any of the shapes?
     */
    public boolean membership(final Point2D p) {
        for (int i = 0; i < size(); i++) {
            if (get(i).get(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many shapes are there in the list?
     */
    public int size() {
        return shapes.size();
    }

    /**
     * Add a shape on the end
     */
    public void add(final BooleanGrid b) {
        if (b == null) {
            throw new IllegalArgumentException("b must not be null");
        }
        if (b != BooleanGrid.NOTHING_THERE) {
            shapes.add(b);
        }
    }

    /**
     * Reverse the order of this list
     */
    public void reverse() {
        Collections.reverse(shapes);
    }

    /**
     * Work out all the polygons forming a set of borders
     */
    public PolygonList borders() {
        final PolygonList result = new PolygonList();
        for (int i = 0; i < size(); i++) {
            final BooleanGrid grid = get(i);
            result.add(grid.allPerimiters());
        }
        return result;
    }

    /**
     * Run through the list, unioning entries in it that share the same material
     * so that the result has just one entry per material.
     */
    public BooleanGridList unionDuplicates() {
        final BooleanGridList result = new BooleanGridList();

        if (size() <= 0) {
            return result;
        }

        if (size() == 1) {
            return this;
        }

        final boolean[] usedUp = new boolean[size()];
        for (int i = 0; i < usedUp.length; i++) {
            usedUp[i] = false;
        }

        for (int i = 0; i < size() - 1; i++) {
            if (!usedUp[i]) {
                BooleanGrid union = get(i);
                final String material = union.getMaterial();
                for (int j = i + 1; j < size(); j++) {
                    if (!usedUp[j]) {
                        final BooleanGrid jg = get(j);
                        if (material.equals(jg.getMaterial())) {
                            union = BooleanGridMath.union(union, jg);
                            usedUp[j] = true;
                        }
                    }
                }
                result.add(union);
            }
        }

        if (!usedUp[size() - 1]) {
            result.add(get(size() - 1));
        }

        return result;
    }

    /**
     * Return a list of unions between the entries in a and b. Only pairs with
     * the same material are unioned. If an element of a has no corresponding
     * element in b, or vice versa, then those elements are returned unmodified
     * in the result.
     */
    public static BooleanGridList unions(final BooleanGridList a, final BooleanGridList b) {
        final BooleanGridList result = new BooleanGridList();

        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if (a == b) {
            return a;
        }
        if (a.size() <= 0) {
            return b;
        }
        if (b.size() <= 0) {
            return a;
        }

        final boolean[] bMatched = new boolean[b.size()];
        for (int i = 0; i < bMatched.length; i++) {
            bMatched[i] = false;
        }

        for (int i = 0; i < a.size(); i++) {
            final BooleanGrid abg = a.get(i);
            boolean aMatched = false;
            for (int j = 0; j < b.size(); j++) {
                final BooleanGrid grid = b.get(j);
                if (abg.getMaterial().equals(grid.getMaterial())) {
                    result.add(BooleanGridMath.union(abg, grid));
                    bMatched[j] = true;
                    aMatched = true;
                    break;
                }
            }
            if (!aMatched) {
                result.add(abg);
            }
        }

        for (int i = 0; i < bMatched.length; i++) {
            if (!bMatched[i]) {
                result.add(b.get(i));
            }
        }

        return result.unionDuplicates();
    }

    /**
     * Return a list of intersections between the entries in a and b. Only pairs
     * with the same material are intersected. If an element of a has no
     * corresponding element in b, or vice versa, then no entry is returned for
     * them.
     */
    public static BooleanGridList intersections(final BooleanGridList a, final BooleanGridList b) {
        if (a == b) {
            return a;
        }
        final BooleanGridList result = new BooleanGridList();
        if (a == null || b == null) {
            return result;
        }
        if (a.size() <= 0 || b.size() <= 0) {
            return result;
        }

        for (int i = 0; i < a.size(); i++) {
            final BooleanGrid abg = a.get(i);
            for (int j = 0; j < b.size(); j++) {
                final BooleanGrid grid = b.get(j);
                if (abg.getMaterial().equals(grid.getMaterial())) {
                    result.add(BooleanGridMath.intersection(abg, grid));
                }
            }
        }
        return result.unionDuplicates();
    }

    /**
     * Return a list of differences between the entries in a and b. Only pairs
     * with the same material are subtracted unless ignoreAttributes is true,
     * whereupon everything in b is subtracted from everything in a. If
     * attributes are considered and an element of a has no corresponding
     * element in b, then an entry equal to a is returned for that.
     */
    public static BooleanGridList differences(final BooleanGridList a, final BooleanGridList b) {
        final BooleanGridList result = new BooleanGridList();

        if (a == null) {
            return result;
        }
        if (b == null) {
            return a;
        }
        if (a == b) {
            return result;
        }
        if (a.size() <= 0) {
            return result;
        }
        if (b.size() <= 0) {
            return a;
        }

        for (int i = 0; i < a.size(); i++) {
            final BooleanGrid abg = a.get(i);
            boolean aMatched = false;
            for (int j = 0; j < b.size(); j++) {
                final BooleanGrid grid = b.get(j);
                if (abg.getMaterial().equals(grid.getMaterial())) {
                    result.add(BooleanGridMath.difference(abg, grid));
                    aMatched = true;
                    break;
                }
            }
            if (!aMatched) {
                result.add(abg);
            }

        }
        return result.unionDuplicates();
    }

    @Override
    public Iterator<BooleanGrid> iterator() {
        return shapes.iterator();
    }

    public BooleanGridList subtractPolygons(final PolygonList polygons, final double width) {
        final BooleanGridList result = new BooleanGridList();
        for (final BooleanGrid grid : shapes) {
            result.add(grid.subtractPolygons(polygons, width));
        }
        return result;
    }
}
