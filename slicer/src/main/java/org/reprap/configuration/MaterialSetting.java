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

import javax.vecmath.Color3f;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

public class MaterialSetting implements NamedSetting {
    @XmlAttribute
    @XmlID
    private String name; // text
    @XmlElement
    private double diameter; // mm 1.75
    @XmlJavaTypeAdapter(Color3fXmlAdapter.class)
    private Color3f color; // rgb value

    public double getDiameter() {
        return diameter;
    }

    void setDiameter(final double diameter) {
        this.diameter = diameter;
    }

    public Color3f getColor() {
        return color;
    }

    void setColor(final Color3f color) {
        this.color = color;
    }

    @Override
    public String getName() {
        return name;
    }

    void setName(final String name) {
        this.name = name;
    }
}
