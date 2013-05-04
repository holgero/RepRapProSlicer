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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import javax.vecmath.Color3f;

import org.junit.Test;

public class Color3fXmlAdapterUnmarshallTest {
    final Color3fXmlAdapter adapter = new Color3fXmlAdapter();

    @Test
    public void testMapBlack() {
        final Color3f adapted = adapter.unmarshal(new XmlRgbColor());
        assertThat(adapted.x, is(0.0f));
        assertThat(adapted.y, is(0.0f));
        assertThat(adapted.z, is(0.0f));
    }

    @Test
    public void testMapRed() {
        final Color3f adapted = adapter.unmarshal(new XmlRgbColor(1.0f, 0.0f, 0.0f));
        assertThat(adapted.x, is(1.0f));
        assertThat(adapted.y, is(0.0f));
        assertThat(adapted.z, is(0.0f));
    }

    @Test
    public void testMapGreen() {
        final Color3f adapted = adapter.unmarshal(new XmlRgbColor(0.0f, 1.0f, 0.0f));
        assertThat(adapted.x, is(0.0f));
        assertThat(adapted.y, is(1.0f));
        assertThat(adapted.z, is(0.0f));
    }

    @Test
    public void testMapBlue() {
        final Color3f adapted = adapter.unmarshal(new XmlRgbColor(0.0f, 0.0f, 1.0f));
        assertThat(adapted.x, is(0.0f));
        assertThat(adapted.y, is(0.0f));
        assertThat(adapted.z, is(1.0f));
    }

    @Test
    public void testMapWhite() {
        final XmlRgbColor adapted = adapter.marshal(new Color3f(1.0f, 1.0f, 1.0f));
        assertThat(adapted.getRed(), is(1.0f));
        assertThat(adapted.getGreen(), is(1.0f));
        assertThat(adapted.getBlue(), is(1.0f));
    }
}
