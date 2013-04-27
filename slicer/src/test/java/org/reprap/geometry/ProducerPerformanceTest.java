package org.reprap.geometry;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.reprap.gcode.GCodePrinter;
import org.reprap.geometry.polygons.BooleanGridWalkerTest;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.graphicio.RFO;

public class ProducerPerformanceTest {

    private AllSTLsToBuild stls;

    @Before
    public void setUp() throws Exception {
        stls = RFO.load(getClass().getClassLoader().getResource("euro_chip.rfo").getPath());
    }

    @Test
    //    @Ignore("this takes too long for normal execution")
    public void testSlicingPerformance() throws IOException {
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
    //    @Ignore("this takes too long for normal execution")
    public void testGridWalkerPerformance() throws Exception {
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            new BooleanGridWalkerTest().testMarchBigC();
        }
        final long end = System.currentTimeMillis();
        System.out.println("Walking took " + (end - start) + " ms.");
    }
}
