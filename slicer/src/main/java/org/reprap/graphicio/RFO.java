package org.reprap.graphicio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.swing.JOptionPane;
import javax.vecmath.Matrix4d;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.reprap.geometry.polyhedra.AllSTLsToBuild;
import org.reprap.geometry.polyhedra.STLObject;
import org.reprap.utilities.Debug;

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
     * The name of the RFO file.
     */
    private final String fileName;
    /**
     * The unique file names;
     */
    private List<String> uNames;
    /**
     * The directory in which it is.
     */
    private String path;
    /**
     * The temporary directory
     */
    private final File tempDir;
    /**
     * The collection of objects being written out or read in.
     */
    private AllSTLsToBuild astl;
    /**
     * The XML output for the legend file.
     */
    private RfoXmlRenderer xml;

    /**
     * The constructor is the same whether we're reading or writing. fn is where
     * to put or get the rfo file from. as is all the things to write; set that
     * null when reading.
     */
    private RFO(final String fn, final AllSTLsToBuild as) throws IOException {
        astl = as;
        uNames = null;
        final int sepIndex = fn.lastIndexOf(File.separator);
        final int fIndex = fn.indexOf("file:");
        fileName = fn.substring(sepIndex + 1, fn.length());
        if (sepIndex >= 0) {
            if (fIndex >= 0) {
                path = fn.substring(fIndex + 5, sepIndex + 1);
            } else {
                path = fn.substring(0, sepIndex + 1);
            }
        } else {
            path = "";
        }
        tempDir = File.createTempFile("rfo", null);
        tempDir.delete();
        tempDir.mkdir();
    }

    /**
     * Copy each unique STL file to a directory. Files used more than once are
     * only copied once.
     * 
     * @throws IOException
     */
    public static List<String> copySTLs(final AllSTLsToBuild astltb, final File rfod) throws IOException {
        int u = 0;
        final List<String> uniqueNames = new ArrayList<String>();
        for (int i = 0; i < astltb.size(); i++) {
            for (int subMod1 = 0; subMod1 < astltb.get(i).size(); subMod1++) {
                final File file = astltb.get(i).fileAndDirectioryItCameFrom(subMod1);
                astltb.get(i).setUnique(subMod1, u);
                for (int j = 0; j < i; j++) {
                    for (int subMod2 = 0; subMod2 < astltb.get(j).size(); subMod2++) {
                        if (file.equals(astltb.get(j).fileAndDirectioryItCameFrom(subMod2))) {
                            astltb.get(i).setUnique(subMod1, astltb.get(j).getUnique(subMod2));
                            break;
                        }
                    }
                }
                if (astltb.get(i).getUnique(subMod1) == u) {
                    final String un = astltb.get(i).fileItCameFrom(subMod1);
                    FileUtils.copyFile(file, new File(rfod, un));
                    uniqueNames.add(un);

                    final String csgFileName = CSGReader.CSGFileExists(file.getAbsolutePath());
                    if (csgFileName != null) {
                        final File csgFile = new File(csgFileName);
                        FileUtils.copyFile(csgFile, new File(rfod, csgFile.getName()));
                    }
                    u++;
                }
            }
        }
        return uniqueNames;
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
     */
    private void createLegend(final File rfoDir) {
        if (uNames == null) {
            Debug.getInstance().errorMessage("RFO.createLegend(): no list of unique names saved.");
            return;
        }
        xml = new RfoXmlRenderer(new File(rfoDir, legendName), "reprap-fab-at-home-build version=\"0.1\"");
        for (int i = 0; i < astl.size(); i++) {
            xml.push("object name=\"object-" + i + "\"");
            xml.push("files");
            final STLObject stlo = astl.get(i);
            for (int subObj = 0; subObj < stlo.size(); subObj++) {
                xml.push("file location=\"" + uNames.get(stlo.getUnique(subObj))
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
     */
    private void compress() throws IOException {
        final ZipOutputStream rfoFile = new ZipOutputStream(new FileOutputStream(path + fileName));
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
     * Warn the user of an overwrite
     */
    public static boolean checkFile(final File file) {
        if (file.exists()) {
            final String[] options = { "OK", "Cancel" };
            final int r = JOptionPane.showOptionDialog(null, "The file " + file.getName() + " exists.  Overwrite it?",
                    "Warning", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            return r == 0;
        }
        return true;
    }

    /**
     * This is what gets called to write an rfo file. It saves all the parts of
     * allSTL in rfo file fn.
     */
    public static void save(String fn, final AllSTLsToBuild allSTL) throws IOException {
        if (!fn.endsWith(".rfo")) {
            fn += ".rfo";
        }
        final RFO rfo = new RFO(fn, allSTL);
        if (!RFO.checkFile(new File(rfo.path, rfo.fileName))) {
            return;
        }
        final File rfoDir = new File(rfo.tempDir, "rfo");
        rfoDir.mkdir();
        rfo.uNames = copySTLs(allSTL, rfoDir);
        rfo.createLegend(rfoDir);
        rfo.compress();
        FileUtils.deleteDirectory(rfo.tempDir);
    }

    /**
     * This uncompresses the zip that is the rfo file into the temporary
     * directory.
     * 
     * @throws IOException
     */
    private void unCompress() throws IOException {
        final ZipFile rfoFile = new ZipFile(path + fileName);
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
    private void interpretLegend() {
        @SuppressWarnings("unused")
        final RfoXmlHandler xi = new RfoXmlHandler(new File(tempDir, "rfo") + "/" + legendName, this);
    }

    /**
     * This is what gets called to read an rfo file from filename fn.
     * 
     * @throws IOException
     */
    public static AllSTLsToBuild load(String fn) throws IOException {
        if (!fn.endsWith(".rfo")) {
            fn += ".rfo";
        }
        final RFO rfo = new RFO(fn, null);
        rfo.astl = new AllSTLsToBuild();
        rfo.unCompress();
        try {
            rfo.interpretLegend();
        } catch (final Exception e) {
            Debug.getInstance().errorMessage("RFO.load(): exception - " + e.toString());
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    FileUtils.deleteDirectory(rfo.tempDir);
                } catch (final IOException e) {
                }
            }
        });
        return rfo.astl;
    }

    AllSTLsToBuild getAstl() {
        return astl;
    }

    File getRfoDir() {
        return new File(tempDir, "rfo");
    }
}
