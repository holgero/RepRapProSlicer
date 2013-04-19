package org.reprap.geometry.polyhedra;

import java.io.File;

import javax.media.j3d.BranchGroup;


final class STLFileContents {
    private final File sourceFile; // The STL file I was loaded from
    private final BranchGroup stl; // The actual STL geometry
    private final CSG3D csg; // CSG if available
    private final Attributes att; // The attributes associated with it
    private final double volume; // Useful to know
    private int unique;

    STLFileContents(final File file, final BranchGroup st, final CSG3D c, final Attributes a, final double v) {
        sourceFile = file;
        stl = st;
        csg = c;
        att = a;
        volume = v;
        unique = 0;
    }

    void setUnique(final int i) {
        unique = i;
    }

    int getUnique() {
        return unique;
    }

    File getSourceFile() {
        return sourceFile;
    }

    BranchGroup getStl() {
        return stl;
    }

    CSG3D getCsg() {
        return csg;
    }

    Attributes getAtt() {
        return att;
    }

    double getVolume() {
        return volume;
    }
}