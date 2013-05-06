package org.reprap.io.rfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.media.j3d.Transform3D;

import org.reprap.configuration.CurrentConfiguration;
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
     * The current XML item
     */
    private String element = "";

    /**
     * Transfom matrix to get an item in the right place.
     */
    private final double[] mElements = new double[16];

    private Transform3D transform;

    private int rowNumber = 0;

    private final List<STLObject> stls = new ArrayList<>();

    private final File rfoDir;

    private final CurrentConfiguration currentConfiguration;

    RfoXmlHandler(final File rfoDir, final CurrentConfiguration currentConfiguration) {
        this.rfoDir = rfoDir;
        this.currentConfiguration = currentConfiguration;
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
            stl = null;
        } else if (element.equalsIgnoreCase("files")) {
        } else if (element.equalsIgnoreCase("file")) {
            validateFiletype(atts);
            final String location = atts.getValue("location");
            final String material = atts.getValue("material");
            if (stl == null) {
                stl = STLObject.createStlObjectFromFile(new File(rfoDir, location), material, currentConfiguration);
            } else {
                stl.addSTL(new File(rfoDir, location), material, currentConfiguration);
            }
        } else if (element.equalsIgnoreCase("transform3D")) {
            setMToIdentity();
        } else if (element.equalsIgnoreCase("row")) {
            for (int column = 0; column < 4; column++) {
                mElements[rowNumber * 4 + column] = Double.parseDouble(atts.getValue("m" + rowNumber + column));
            }
        } else {
            throw new RuntimeException("XMLIn.startElement(): unrecognised RFO element: " + element);
        }
    }

    private static void validateFiletype(final org.xml.sax.Attributes atts) {
        final String filetype = atts.getValue("filetype");
        if (!filetype.equalsIgnoreCase("application/sla")) {
            throw new RuntimeException("unrecognised object file type (should be \"application/sla\"): " + filetype);
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
        } else if (element.equalsIgnoreCase("transform3D")) {
            if (rowNumber != 4) {
                throw new RuntimeException("XMLIn.endElement(): incomplete Transform3D matrix - last row number is not 4: "
                        + rowNumber);
            }
            transform = new Transform3D(mElements);
        } else if (element.equalsIgnoreCase("row")) {
            rowNumber++;
        } else {
            throw new RuntimeException("XMLIn.endElement(): unrecognised RFO element: " + element);
        }
    }
}