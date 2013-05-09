package org.reprap.io.rfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Matrix4d;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.reprap.configuration.CurrentConfiguration;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.io.csg.CSGReader;
import org.xml.sax.SAXException;

/**
 * A .rfo file is a compressed archive containing multiple objects that are all
 * to be built in a RepRap machine at once. See this web page:
 * 
 * http://reprap.org/bin/view/Main/MultipleMaterialsFiles
 * 
 * for details.
 * 
 * This is the class that handles .rfo files.
 */
public class RFO {
    private static final String legendName = "legend.xml";

    /**
     * Copy each STL file to a directory. Files used more than once are only
     * copied once.
     */
    public static void copySTLs(final AllSTLsToBuild astltb, final File targetDirectory) throws IOException {
        for (int i = 0; i < astltb.size(); i++) {
            for (int j = 0; j < astltb.get(i).size(); j++) {
                final File stlFile = astltb.get(i).getSourceFile(j);
                final String stlFileName = stlFile.getName();
                final File copiedFile = new File(targetDirectory, stlFileName);
                if (!copiedFile.exists()) {
                    FileUtils.copyFile(stlFile, copiedFile);
                }

                final File csgFile = CSGReader.getCsgFile(stlFile);
                if (csgFile != null) {
                    final File copiedCsgFile = new File(targetDirectory, csgFile.getName());
                    if (!copiedCsgFile.exists()) {
                        FileUtils.copyFile(csgFile, copiedCsgFile);
                    }
                }
            }
        }
    }

    public static RFO load(final File file, final CurrentConfiguration currentConfiguration) {
        final RFO rfo = new RFO(new AllSTLsToBuild(), currentConfiguration);
        rfo.load(file);
        return rfo;
    }

    /**
     * The temporary directory
     */
    private final File tempDir;
    /**
     * The collection of objects being written out or read in.
     */
    private final AllSTLsToBuild astl;
    /**
     * The XML output for the legend file.
     */
    private RfoXmlRenderer xml;

    private final CurrentConfiguration currentConfiguration;

    public RFO(final AllSTLsToBuild as, final CurrentConfiguration currentConfiguration) {
        astl = as;
        this.currentConfiguration = currentConfiguration;
        try {
            tempDir = File.createTempFile("rfo", null);
            tempDir.delete();
            tempDir.mkdir();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public RFO(final CurrentConfiguration currentConfiguration) {
        this(new AllSTLsToBuild(), currentConfiguration);
    }

    public void load(final File file) {
        try {
            unCompress(file);
            interpretLegend();
        } catch (final IOException e1) {
            throw new RuntimeException(e1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (final IOException e) {
                }
            }
        });
    }

    public void save(final File file) throws IOException, FileNotFoundException {
        final File rfoDir = new File(tempDir, "rfo");
        rfoDir.mkdir();
        copySTLs(astl, rfoDir);
        createLegend(rfoDir);
        compress(file);
        FileUtils.deleteDirectory(tempDir);
    }

    /**
     * Write a 4x4 homogeneous transform in XML format.
     */
    private void writeTransform(final TransformGroup trans) {
        final Transform3D t = new Transform3D();
        final Matrix4d m = new Matrix4d();
        trans.getTransform(t);
        t.get(m);
        xml.push("transform3D");
        xml.write("row m00=\"" + m.m00 + "\" m01=\"" + m.m01 + "\" m02=\"" + m.m02 + "\" m03=\"" + m.m03 + "\"");
        xml.write("row m10=\"" + m.m10 + "\" m11=\"" + m.m11 + "\" m12=\"" + m.m12 + "\" m13=\"" + m.m13 + "\"");
        xml.write("row m20=\"" + m.m20 + "\" m21=\"" + m.m21 + "\" m22=\"" + m.m22 + "\" m23=\"" + m.m23 + "\"");
        xml.write("row m30=\"" + m.m30 + "\" m31=\"" + m.m31 + "\" m32=\"" + m.m32 + "\" m33=\"" + m.m33 + "\"");
        xml.pop();
    }

    /**
     * Create the legend file
     * 
     * @throws FileNotFoundException
     */
    private void createLegend(final File rfoDir) throws FileNotFoundException {
        if (rfoDir == null) {
            throw new IllegalArgumentException("rfoDir must not be null");
        }
        xml = new RfoXmlRenderer(new File(rfoDir, legendName), "reprap-fab-at-home-build version=\"0.1\"");
        for (int i = 0; i < astl.size(); i++) {
            xml.push("object name=\"object-" + i + "\"");
            xml.push("files");
            final STLObject stlo = astl.get(i);
            for (int subObj = 0; subObj < stlo.size(); subObj++) {
                xml.push("file location=\"" + stlo.getSourceFile(subObj).getName()
                        + "\" filetype=\"application/sla\" material=\"" + stlo.attributes(subObj).getMaterial() + "\"");
                xml.pop();
            }
            xml.pop();
            writeTransform(stlo.trans());
            xml.pop();
        }
        xml.close();
    }

    /**
     * The entire temporary directory with the legend file and the STLs is
     * complete. Compress it into the required rfo file using zip. Note we
     * delete the temporary files as we go along, ending up by deleting the
     * directory containing them.
     * 
     * @param file
     */
    private void compress(final File file) throws IOException {
        final ZipOutputStream rfoFile = new ZipOutputStream(new FileOutputStream(file));
        try {
            final File dirToZip = new File(tempDir, "rfo");
            final String[] fileList = dirToZip.list();
            final byte[] buffer = new byte[4096];
            int bytesIn = 0;

            for (final String element : fileList) {
                final File f = new File(dirToZip, element);
                final FileInputStream fis = new FileInputStream(f);
                try {
                    String zEntry = f.getPath();
                    zEntry = zEntry.substring(tempDir.getPath().length() + 1, zEntry.length());
                    final ZipEntry entry = new ZipEntry(zEntry);
                    rfoFile.putNextEntry(entry);
                    while ((bytesIn = fis.read(buffer)) != -1) {
                        rfoFile.write(buffer, 0, bytesIn);
                    }
                } finally {
                    fis.close();
                }
            }
        } finally {
            rfoFile.close();
        }
    }

    /**
     * This uncompresses the zip that is the rfo file into the temporary
     * directory.
     */
    private void unCompress(final File file) throws IOException {
        final ZipFile rfoFile = new ZipFile(file);
        final Enumeration<? extends ZipEntry> allFiles = rfoFile.entries();
        while (allFiles.hasMoreElements()) {
            final ZipEntry entry = allFiles.nextElement();
            final InputStream inStream = rfoFile.getInputStream(entry);
            try {
                final String name = entry.getName().replace('\\', '/');
                final File element = new File(tempDir, name);
                element.getParentFile().mkdirs();
                final FileOutputStream os = new FileOutputStream(element);
                try {
                    IOUtils.copy(inStream, os);
                } finally {
                    os.close();
                }
            } finally {
                inStream.close();
            }
        }
    }

    /**
     * This reads the legend file and does what it says.
     */
    private void interpretLegend() throws IOException {
        final RfoXmlHandler xi = new RfoXmlHandler(getRfoDir(), currentConfiguration);
        final String legendFilename = new File(tempDir, "rfo") + "/" + legendName;
        try {
            final List<STLObject> stls = xi.parse(legendFilename);
            for (final STLObject stl : stls) {
                astl.add(stl);
            }
        } catch (final SAXException e) {
            throw new RuntimeException("Failed to parse " + legendFilename, e);
        }
    }

    File getRfoDir() {
        return new File(tempDir, "rfo");
    }

    public AllSTLsToBuild getAllStls() {
        return astl;
    }
}
