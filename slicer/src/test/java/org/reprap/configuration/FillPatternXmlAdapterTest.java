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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class FillPatternXmlAdapterTest {
    FillPatternXmlAdapter adapter = new FillPatternXmlAdapter();

    @Test(expected = IllegalArgumentException.class)
    public void testMarshallAnonymousPattern() {
        final FillPattern pattern = new FillPattern() {
            @Override
            public double angle(final int layer) {
                return 0;
            }
        };
        adapter.marshal(pattern);
    }

    @Test
    public void testMarshallRectilinearPattern() {
        final FillPattern pattern = new RectilinearFillPattern(45.0);
        final FillPatternDescriptor descriptor = adapter.marshal(pattern);
        assertThat(descriptor.type, is("rectilinear"));
        assertThat(descriptor.angle, is(45.0));
    }

    @Test
    public void testMarshallLinearPattern() {
        final FillPattern pattern = new LinearFillPattern(60.0);
        final FillPatternDescriptor descriptor = adapter.marshal(pattern);
        assertThat(descriptor.type, is("linear"));
        assertThat(descriptor.angle, is(60.0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnmarshallUnknownType() {
        adapter.unmarshal(new FillPatternDescriptor("honeycomb", 0));
    }

    @Test
    public void testUnmarshallRectilinearPattern() {
        final FillPatternDescriptor descriptor = new FillPatternDescriptor("rectilinear", 30);
        final FillPattern pattern = adapter.unmarshal(descriptor);
        assertThat(pattern, instanceOf(RectilinearFillPattern.class));
        assertThat(pattern.angle(0), is(30.0));
    }

    @Test
    public void testUnmarshallLinearPattern() {
        final FillPatternDescriptor descriptor = new FillPatternDescriptor("linear", 75);
        final FillPattern pattern = adapter.unmarshal(descriptor);
        assertThat(pattern, instanceOf(LinearFillPattern.class));
        assertThat(pattern.angle(1), is(75.0));
    }

}
