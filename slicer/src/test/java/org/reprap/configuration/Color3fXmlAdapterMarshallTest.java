package org.reprap.configuration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import javax.vecmath.Color3f;

import org.junit.Test;

public class Color3fXmlAdapterMarshallTest {

    @Test
    public void testMapBlack() {
        final Color3fXmlAdapter adapter = new Color3fXmlAdapter();
        final XmlRgbColor adapted = adapter.marshal(new Color3f());
        assertThat(adapted.getRed(), is(0.0f));
        assertThat(adapted.getGreen(), is(0.0f));
        assertThat(adapted.getBlue(), is(0.0f));
    }

    @Test
    public void testMapRed() {
        final Color3fXmlAdapter adapter = new Color3fXmlAdapter();
        final XmlRgbColor adapted = adapter.marshal(new Color3f(1.0f, 0.0f, 0.0f));
        assertThat(adapted.getRed(), is(1.0f));
        assertThat(adapted.getGreen(), is(0.0f));
        assertThat(adapted.getBlue(), is(0.0f));
    }

    @Test
    public void testMapGreen() {
        final Color3fXmlAdapter adapter = new Color3fXmlAdapter();
        final XmlRgbColor adapted = adapter.marshal(new Color3f(0.0f, 1.0f, 0.0f));
        assertThat(adapted.getRed(), is(0.0f));
        assertThat(adapted.getGreen(), is(1.0f));
        assertThat(adapted.getBlue(), is(0.0f));
    }

    @Test
    public void testMapBlue() {
        final Color3fXmlAdapter adapter = new Color3fXmlAdapter();
        final XmlRgbColor adapted = adapter.marshal(new Color3f(0.0f, 0.0f, 1.0f));
        assertThat(adapted.getRed(), is(0.0f));
        assertThat(adapted.getGreen(), is(0.0f));
        assertThat(adapted.getBlue(), is(1.0f));
    }

    @Test
    public void testMapWhite() {
        final Color3fXmlAdapter adapter = new Color3fXmlAdapter();
        final XmlRgbColor adapted = adapter.marshal(new Color3f(1.0f, 1.0f, 1.0f));
        assertThat(adapted.getRed(), is(1.0f));
        assertThat(adapted.getGreen(), is(1.0f));
        assertThat(adapted.getBlue(), is(1.0f));
    }
}
