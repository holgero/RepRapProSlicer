package org.reprap.io.rfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.media.j3d.Transform3D;

import org.reprap.geometry.polyhedra.STLObject;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

final class RfoXmlHandler extends DefaultHandler {
    /**
     * The STL being read
     */
    private STLObject stl;

    /**
     * The first of a list of STLs being read.
     */
    private STLObject firstSTL;
    /**
     * The current XML item
     */
    private String element = "";

    /**
     * File location for reading (eg for an input STL file).
     */
    private String location = "";

    /**
     * What type of file (Only STLs supported at the moment).
     */
    private String filetype = "";

    /**
     * The name of the material (i.e. extruder) that this item is made from.
     */
    private String material = "";

    /**
     * Transfom matrix to get an item in the right place.
     */
    private final double[] mElements = new double[16];

    private Transform3D transform;

    private int rowNumber = 0;

    private final List<STLObject> stls = new ArrayList<>();

    private final File rfoDir;

    RfoXmlHandler(final File rfoDir) {
        this.rfoDir = rfoDir;
        setMToIdentity();
        stls.clear();
    }

    List<STLObject> parse(final String legendFile) throws SAXException, IOException {
        final XMLReader xr = XMLReaderFactory.createXMLReader();
        xr.setContentHandler(this);
        xr.setErrorHandler(this);
        xr.parse(new InputSource(legendFile));

        return stls;
    }

    /**
     * Initialise the matrix to the identity matrix.
     */
    private void setMToIdentity() {
        for (rowNumber = 0; rowNumber < 4; rowNumber++) {
            for (int column = 0; column < 4; column++) {
                if (rowNumber == column) {
                    mElements[rowNumber * 4 + column] = 1;
                } else {
                    mElements[rowNumber * 4 + column] = 0;
                }
            }
        }
        transform = new Transform3D(mElements);
        rowNumber = 0;
    }

    /**
     * Start an element
     */
    @Override
    public void startElement(final String uri, final String name, final String qName, final org.xml.sax.Attributes atts) {
        if (uri.equals("")) {
            element = qName;
        } else {
            element = name;
        }

        if (element.equalsIgnoreCase("reprap-fab-at-home-build")) {
        } else if (element.equalsIgnoreCase("object")) {
            stl = new STLObject();
            firstSTL = null;
        } else if (element.equalsIgnoreCase("files")) {
        } else if (element.equalsIgnoreCase("file")) {
            location = atts.getValue("location");
            filetype = atts.getValue("filetype");
            material = atts.getValue("material");
            if (!filetype.equalsIgnoreCase("application/sla")) {
                throw new RuntimeException(
                        "XMLIn.startElement(): unreconised object file type (should be \"application/sla\"): " + filetype);
            }
        } else if (element.equalsIgnoreCase("transform3D")) {
            setMToIdentity();
        } else if (element.equalsIgnoreCase("row")) {
            for (int column = 0; column < 4; column++) {
                mElements[rowNumber * 4 + column] = Double.parseDouble(atts.getValue("m" + rowNumber + column));
            }
        } else {
            throw new RuntimeException("XMLIn.startElement(): unreconised RFO element: " + element);
        }
    }

    @Override
    public void endElement(final String uri, final String name, final String qName) {
        if (uri.equals("")) {
            element = qName;
        } else {
            element = name;
        }
        if (element.equalsIgnoreCase("reprap-fab-at-home-build")) {

        } else if (element.equalsIgnoreCase("object")) {
            stl.setTransform(transform);
            stls.add(stl);
        } else if (element.equalsIgnoreCase("files")) {

        } else if (element.equalsIgnoreCase("file")) {
            final org.reprap.geometry.polyhedra.Attributes att = stl.addSTL(new File(rfoDir, location), null, null, firstSTL);
            if (firstSTL == null) {
                firstSTL = stl;
            }
            att.setMaterial(material);
            location = "";
            filetype = "";
            material = "";

        } else if (element.equalsIgnoreCase("transform3D")) {
            if (rowNumber != 4) {
                throw new RuntimeException("XMLIn.endElement(): incomplete Transform3D matrix - last row number is not 4: "
                        + rowNumber);
            }
            transform = new Transform3D(mElements);
        } else if (element.equalsIgnoreCase("row")) {
            rowNumber++;
        } else {
            throw new RuntimeException("XMLIn.endElement(): unreconised RFO element: " + element);
        }
    }
}