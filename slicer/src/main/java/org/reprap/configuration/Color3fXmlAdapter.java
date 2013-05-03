package org.reprap.configuration;

import javax.vecmath.Color3f;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class Color3fXmlAdapter extends XmlAdapter<XmlRgbColor, Color3f> {

    @Override
    public Color3f unmarshal(final XmlRgbColor v) {
        return new Color3f(v.getRed(), v.getGreen(), v.getBlue());
    }

    @Override
    public XmlRgbColor marshal(final Color3f v) {
        return new XmlRgbColor(v.x, v.y, v.z);
    }

}
