package org.reprap.geometry.polyhedra;

import java.io.File;

import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;

public final class STLFileContents {
    private final File sourceFile; // The STL file I was loaded from
    private final BranchGroup stl; // The actual STL geometry
    private CSG3D csg; // CSG if available
    private Attributes attribute; // The attributes associated with it
    private final double volume; // Useful to know
    private int unique;
    private final BoundingBox bbox;

    public STLFileContents(final File sourceFile, final BranchGroup stl, final CSG3D csg, final double volume,
            final BoundingBox bbox) {
        this.sourceFile = sourceFile;
        this.stl = stl;
        this.csg = csg;
        this.volume = volume;
        this.bbox = bbox;
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

    Attributes getAttribute() {
        return attribute;
    }

    double getVolume() {
        return volume;
    }

    void setCsg(final CSG3D csgResult) {
        csg = csgResult;
    }

    void setAttribute(final Attributes attribute) {
        this.attribute = attribute;
    }

    public BoundingBox getBbox() {
        return bbox;
    }
}