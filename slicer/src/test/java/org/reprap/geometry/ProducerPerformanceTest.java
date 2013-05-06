/* RepRapProSlicer creates G-Code from geometry files.
 *
 *  Copyright (C) 2013  Holger Oehm
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.reprap.geometry;

import java.io.File;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;
import org.reprap.geometry.polygons.BooleanGridWalkerTest;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.io.rfo.RFO;

public class ProducerPerformanceTest {

    @Test
    @Ignore("needs javax.media.j3d")
    public void testSlicingPerformance() throws IOException {
        final AllSTLsToBuild stls = RFO.load(getClass().getClassLoader().getResource("euro_chip.rfo").getPath());
        final Producer producer = new Producer(null, stls, new ProductionProgressListener() {
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
    public void testGridWalkerPerformance() {
        final int iterations = 100;
        final long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            new BooleanGridWalkerTest().testMarchBigC();
        }
        final long end = System.currentTimeMillis();
        System.out.println("Walking " + iterations + " iterations took " + (end - start) + " ms.");
    }
}
