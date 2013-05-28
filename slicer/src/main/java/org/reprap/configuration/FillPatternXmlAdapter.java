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
package org.reprap.configuration;

import javax.xml.bind.annotation.adapters.XmlAdapter;


public class FillPatternXmlAdapter extends XmlAdapter<FillPatternDescriptor, FillPattern> {

    private static final String RECTILINEAR_TYPE = "rectilinear";
    private static final String LINEAR_TYPE = "linear";

    @Override
    public FillPattern unmarshal(final FillPatternDescriptor descriptor) {
        switch (descriptor.type) {
        case RECTILINEAR_TYPE:
            return new RectilinearFillPattern(descriptor.angle);
        case LINEAR_TYPE:
            return new LinearFillPattern(descriptor.angle);
        default:
            throw new IllegalArgumentException("Cannot unmarshal " + descriptor.type + ".");
        }
    }

    @Override
    public FillPatternDescriptor marshal(final FillPattern pattern) {
        if (pattern instanceof RectilinearFillPattern) {
            final RectilinearFillPattern rectilinearFillPattern = (RectilinearFillPattern) pattern;
            return new FillPatternDescriptor(RECTILINEAR_TYPE, rectilinearFillPattern.getFillAngle());
        } else if (pattern instanceof LinearFillPattern) {
            final LinearFillPattern linearFillPattern = (LinearFillPattern) pattern;
            return new FillPatternDescriptor(LINEAR_TYPE, linearFillPattern.getFillAngle());
        } else {
            throw new IllegalArgumentException("Cannot marshal " + pattern.getClass().getName() + ".");
        }
    }

}
