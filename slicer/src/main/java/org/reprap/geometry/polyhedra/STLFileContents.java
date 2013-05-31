package org.reprap.geometry.polyhedra;

import java.io.File;

import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;

public final class STLFileContents {
    private final File sourceFile; // The STL file I was loaded from
    private final BranchGroup stl; // The actual STL geometry
    private Attributes attribute; // The attributes associated with it
    private final double volume; // Useful to know
    private final BoundingBox bbox;

    public STLFileContents(final File sourceFile, final BranchGroup stl, final double volume, final BoundingBox bbox) {
        this.sourceFile = sourceFile;
        this.stl = stl;
        this.volume = volume;
        this.bbox = bbox;
    }

    File getSourceFile() {
        return sourceFile;
    }

    BranchGroup getStl() {
        return stl;
    }

    Attributes getAttribute() {
        return attribute;
    }

    double getVolume() {
        return volume;
    }

    void setAttribute(final Attributes attribute) {
        this.attribute = attribute;
    }

    public BoundingBox getBbox() {
        return bbox;
    }
}