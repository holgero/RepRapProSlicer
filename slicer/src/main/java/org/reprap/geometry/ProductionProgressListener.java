package org.reprap.geometry;

public interface ProductionProgressListener {

    void productionProgress(int layer, int totalLayers);

}
