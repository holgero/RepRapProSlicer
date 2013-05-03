package org.reprap.configuration;

import javax.xml.bind.annotation.XmlAttribute;

public class XmlRgbColor {

    private float red;
    private float green;
    private float blue;

    public XmlRgbColor() {
    }

    public XmlRgbColor(final float red, final float green, final float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    @XmlAttribute
    public float getRed() {
        return red;
    }

    @XmlAttribute
    public float getGreen() {
        return green;
    }

    @XmlAttribute
    public float getBlue() {
        return blue;
    }

    public void setRed(final float red) {
        this.red = red;
    }

    public void setGreen(final float green) {
        this.green = green;
    }

    public void setBlue(final float blue) {
        this.blue = blue;
    }

}
