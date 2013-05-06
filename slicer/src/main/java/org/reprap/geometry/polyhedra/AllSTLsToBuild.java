package org.reprap.geometry.polyhedra;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reprap.io.rfo.RFO;

/**
 * This class holds a list of STLObjects that represents everything that is to
 * be built.
 * 
 * An STLObject may consist of items from several STL files, possible of
 * different materials. But they are all tied together relative to each other in
 * space.
 * 
 * @author Adrian
 */
public class AllSTLsToBuild {
    private static final Logger LOGGER = LogManager.getLogger(AllSTLsToBuild.class);

    /**
     * The list of things to be built
     */
    private List<STLObject> stls;

    /**
     * New list of things to be built for reordering
     */
    private List<STLObject> newstls;

    public AllSTLsToBuild() {
        stls = new ArrayList<STLObject>();
        newstls = null;
    }

    public void add(final STLObject s) {
        stls.add(s);
    }

    /**
     * Add a new STLObject somewhere in the list
     */
    public void add(final int index, final STLObject s) {
        stls.add(index, s);
    }

    /**
     * Add a new collection
     */
    public void add(final AllSTLsToBuild a) {
        for (int i = 0; i < a.size(); i++) {
            stls.add(a.get(i));
        }
    }

    /**
     * Get the i-th STLObject
     */
    public STLObject get(final int i) {
        return stls.get(i);
    }

    public void remove(final int i) {
        stls.remove(i);
    }

    /**
     * Find an object in the list
     */
    private int findSTL(final STLObject st) {
        if (size() <= 0) {
            LOGGER.error("AllSTLsToBuild.findSTL(): no objects to pick from!");
            return -1;
        }
        int index = -1;
        for (int i = 0; i < size(); i++) {
            if (get(i) == st) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            LOGGER.error("AllSTLsToBuild.findSTL(): dud object submitted.");
            return -1;
        }
        return index;
    }

    /**
     * Find an object in the list and return the next one.
     */
    public STLObject getNextOne(final STLObject st) {
        int index = findSTL(st);
        index++;
        if (index >= size()) {
            index = 0;
        }
        return get(index);
    }

    /**
     * Return the number of objects.
     */
    public int size() {
        return stls.size();
    }

    /**
     * Create an OpenSCAD (http://www.openscad.org/) program that will read
     * everything in in the same pattern as it is stored here. It can then be
     * written by OpenSCAD as a single STL.
     */
    private String toSCAD() {
        String result = "union()\n{\n";
        for (int i = 0; i < size(); i++) {
            result += get(i).toSCAD();
        }
        result += "}";
        return result;
    }

    /**
     * Write everything to an OpenSCAD program.
     */
    public void saveSCAD(final File file) {
        try {
            final File directory = file.getParentFile();
            if (!directory.exists()) {
                directory.mkdir();
            }
            RFO.copySTLs(this, directory);
            final PrintWriter out = new PrintWriter(new FileWriter(file));
            try {
                out.println(toSCAD());
            } finally {
                out.close();
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reorder the list under user control. The user sends items from the old
     * list one by one. These are added to a new list in that order. When
     * there's only one left that is added last automatically.
     * 
     * Needless to say, this process must be carried through to completion. The
     * function returns true while the process is ongoing, false when it's
     * complete.
     */
    public boolean reorderAdd(final STLObject st) {
        if (newstls == null) {
            newstls = new ArrayList<STLObject>();
        }

        final int index = findSTL(st);
        newstls.add(get(index));
        stls.remove(index);

        if (stls.size() > 1) {
            return true;
        }

        newstls.add(get(0));
        stls = newstls;
        newstls = null;

        return false;
    }
}
