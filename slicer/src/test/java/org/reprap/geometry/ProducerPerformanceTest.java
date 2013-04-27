package org.reprap.geometry;

import java.io.File;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.polygons.BooleanGridWalkerTest;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.graphicio.RFO;

public class ProducerPerformanceTest {

    @Test
    @Ignore("needs javax.media.j3d")
    public void testSlicingPerformance() throws IOException {
        final AllSTLsToBuild stls = RFO.load(getClass().getClassLoader().getResource("euro_chip.rfo").getPath());
        final Producer producer = new Producer(new GCodePrinter(), stls, new ProductionProgressListener() {
            @Override
            public void productionProgress(final int layer, final int totalLayers) {
            }
        }, false);
        final long start = System.currentTimeMillis();
        producer.produce();
        final long end = System.currentTimeMillis();
        System.out.println("Slicing took " + (end - start) + " ms.");
        new File("null.gcode").delete();
    }

    @Test
    public void testGridWalkerPerformance() throws Exception {
        final int iterations = 100;
        final long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            new BooleanGridWalkerTest().testMarchBigC();
        }
        final long end = System.currentTimeMillis();
        System.out.println("Walking " + iterations + " iterations took " + (end - start) + " ms.");
    }
}
