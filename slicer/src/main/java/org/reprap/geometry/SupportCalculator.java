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

import org.reprap.configuration.CurrentConfiguration;
import org.reprap.configuration.MaterialSetting;
import org.reprap.configuration.PrintSetting;
import org.reprap.geometry.grids.BooleanGrid;
import org.reprap.geometry.grids.BooleanGridMath;
import org.reprap.geometry.grids.Hatcher;
import org.reprap.geometry.polygons.HalfPlane;
import org.reprap.geometry.polygons.PolygonList;

public class SupportCalculator {
    private final CurrentConfiguration currentConfiguration;
    private final SliceCache cache;

    SupportCalculator(final CurrentConfiguration currentConfiguration, final int sliceCacheSize, final int stlCount) {
        this.currentConfiguration = currentConfiguration;
        cache = new SliceCache(sliceCacheSize, stlCount);
    }

    /**
     * Compute the support hatching polygons for this set of patterns
     */
    PolygonList computeSupport(final int stl, final Slice slice, final int layer) {
        final int supportExtruderNo = currentConfiguration.getPrintSetting().getSupportExtruder();
        final MaterialSetting supportMaterial = currentConfiguration.getMaterials().get(supportExtruderNo);
        // Union of everything in this layer because that is everywhere that support _isn't_ needed.
        BooleanGrid unionOfThisLayer = slice.unionMaterials(supportMaterial.getName());

        // Get the layer above and union it with this layer.  That's what needs
        // support on the next layer down.
        final BooleanGrid previousSupport = cache.getSupport(layer + 1, stl);
        if (previousSupport == null) {
            cache.setSupport(unionOfThisLayer, layer, stl);
            return new PolygonList();
        }

        cache.setSupport(BooleanGridMath.union(previousSupport, unionOfThisLayer), layer, stl);
        if (!unionOfThisLayer.isEmpty()) {
            // Expand the union of this layer a bit, so that any support is a little clear of this layer's boundaries.
            unionOfThisLayer = unionOfThisLayer.createOffsetGrid(0.5);
        }

        // Now we subtract the union of this layer from all the stuff requiring support in the layer above.
        final BooleanGrid support = BooleanGridMath.difference(previousSupport, unionOfThisLayer);
        return hatchSupport(support, layer, currentConfiguration);
    }

    private static PolygonList hatchSupport(final BooleanGrid grid, final int layer,
            final CurrentConfiguration currentConfiguration) {
        final PrintSetting printSetting = currentConfiguration.getPrintSetting();
        final double infillWidth = printSetting.getSupportSpacing();
        final HalfPlane hatchLine = LayerRules.getHatchLine(layer, printSetting.getSupportPattern());
        return new Hatcher(grid).hatch(hatchLine, infillWidth, printSetting.isPathOptimize());
    }
}
