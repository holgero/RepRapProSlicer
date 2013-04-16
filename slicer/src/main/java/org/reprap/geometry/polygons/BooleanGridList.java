package org.reprap.geometry.polygons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.reprap.utilities.Debug;

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
            Debug.getInstance().errorMessage("BooleanGridList.add(): attempt to add null BooleanGrid.");
            return;
        }
        if (b != BooleanGrid.nullBooleanGrid()) {
            shapes.add(b);
        }
    }

    /**
     * Reverse the order of the list
     */
    public BooleanGridList reverse() {
        final BooleanGridList result = new BooleanGridList();
        for (int i = size() - 1; i >= 0; i--) {
            result.add(get(i));
        }
        return result;
    }

    /**
     * Work out all the polygons forming a set of borders
     */
    public PolygonList borders() {
        final PolygonList result = new PolygonList();
        for (int i = 0; i < size(); i++) {
            final BooleanGrid grid = get(i);
            result.add(grid.allPerimiters(grid.attribute()));
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
                final int iExId = union.attribute().getExtruder().getID();
                for (int j = i + 1; j < size(); j++) {
                    if (!usedUp[j]) {
                        final BooleanGrid jg = get(j);
                        if (iExId == jg.attribute().getExtruder().getID()) {
                            union = BooleanGrid.union(union, jg);
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
     * the same extruder are unioned. If an element of a has no corresponding
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
                if (abg.attribute().getExtruder().getID() == grid.attribute().getExtruder().getID()) {
                    result.add(BooleanGrid.union(abg, grid));
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
     * with the same extruder are intersected. If an element of a has no
     * corresponding element in b, or vice versa, then no entry is returned for
     * them.
     */
    public static BooleanGridList intersections(final BooleanGridList a, final BooleanGridList b) {
        final BooleanGridList result = new BooleanGridList();
        if (a == null || b == null) {
            return result;
        }
        if (a == b) {
            return a;
        }
        if (a.size() <= 0 || b.size() <= 0) {
            return result;
        }

        for (int i = 0; i < a.size(); i++) {
            final BooleanGrid abg = a.get(i);
            for (int j = 0; j < b.size(); j++) {
                final BooleanGrid grid = b.get(j);
                if (abg.attribute().getExtruder().getID() == grid.attribute().getExtruder().getID()) {
                    result.add(BooleanGrid.intersection(abg, grid));
                    break;
                }
            }
        }
        return result.unionDuplicates();
    }

    /**
     * Return only those elements in the list that have no support material
     * specified
     */
    public BooleanGridList cullNoSupport() {
        final BooleanGridList result = new BooleanGridList();

        for (int i = 0; i < size(); i++) {
            if (get(i).attribute().getExtruder().getSupportExtruderNumber() < 0) {
                result.add(get(i));
            }
        }

        return result;
    }

    /**
     * Return a list of differences between the entries in a and b. Only pairs
     * with the same attribute are subtracted unless ignoreAttributes is true,
     * whereupon everything in b is subtracted from everything in a. If
     * attributes are considered and an element of a has no corresponding
     * element in b, then an entry equal to a is returned for that.
     * 
     * If onlyNullSupport is true then only entries in a with support equal to
     * null are considered. Otherwise ordinary set difference is returned.
     */
    public static BooleanGridList differences(final BooleanGridList a, final BooleanGridList b, final boolean ignoreAttributes) {
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
                if (ignoreAttributes || (abg.attribute().getExtruder().getID() == grid.attribute().getExtruder().getID())) {
                    result.add(BooleanGrid.difference(abg, grid, abg.attribute()));
                    if (!ignoreAttributes) {
                        aMatched = true;
                        break;
                    }
                }
            }
            if (!aMatched && !ignoreAttributes) {
                result.add(abg);
            }

        }
        return result.unionDuplicates();
    }

    @Override
    public Iterator<BooleanGrid> iterator() {
        return shapes.iterator();
    }
}
