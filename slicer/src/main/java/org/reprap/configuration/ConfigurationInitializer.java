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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ConfigurationInitializer {
    private static final Logger LOGGER = LogManager.getLogger(ConfigurationInitializer.class);
    private static final String DISTRIBUTION_PROPERTIES_DIR_ = "reprap-configurations";
    private final File reprapDirectory;
    private final JAXBContext context;
    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;

    ConfigurationInitializer(final File reprapDirectory) {
        this.reprapDirectory = reprapDirectory;
        try {
            context = JAXBContext.newInstance(CurrentConfiguration.class);
            marshaller = context.createMarshaller();
            unmarshaller = context.createUnmarshaller();
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    CurrentConfiguration loadCurrentConfiguration() {
        try {
            return loadCurrentConfigurationUnsafe();
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CurrentConfiguration loadCurrentConfigurationUnsafe() throws PropertyException, JAXBException, IOException {
        final File xmlFile = new File(reprapDirectory, "current.xml");
        if (!xmlFile.exists()) {
            LOGGER.info("No current configuration found " + xmlFile);
            if (!provideConfigurationFromOldPropertyFile(xmlFile)) {
                reprapDirectory.mkdirs();
                LOGGER.info("Creating new current configuration from distribution files");
                provideDefaultConfiguration();
            }
        } else {
            LOGGER.info("Reading configuration from " + xmlFile);
        }
        return (CurrentConfiguration) unmarshaller.unmarshal(xmlFile);
    }

    private boolean provideConfigurationFromOldPropertyFile(final File xmlFile) throws PropertyException, JAXBException {
        if (reprapDirectory.isDirectory()) {
            LOGGER.info(reprapDirectory + " exists, trying to read property file from there.");
            final PropertyPreferencesConverter converter = new PropertyPreferencesConverter(reprapDirectory);
            final CurrentConfiguration currentConfiguration = converter.loadCurrentConfigurationFromPropertiesFile();
            if (currentConfiguration != null) {
                LOGGER.info("Current configuration loaded from: " + converter.getPropertiesFile());
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                marshaller.marshal(currentConfiguration, xmlFile);
                LOGGER.info("A new configuration file has been written to: " + xmlFile);
                return true;
            }
            LOGGER.warn("Failed to read old properties file from " + reprapDirectory);

        }
        return false;
    }

    private void provideDefaultConfiguration() throws IOException {
        final URL distributionConfiguration = ClassLoader.getSystemResource(DISTRIBUTION_PROPERTIES_DIR_);
        switch (distributionConfiguration.getProtocol()) {
        case "file":
            FileUtils.copyDirectory(new File(URI.create(distributionConfiguration.toString())), reprapDirectory);
            break;
        case "jar":
            copyJarTree(distributionConfiguration, reprapDirectory);
            break;
        default:
            throw new IllegalArgumentException("Cant copy resource stream from " + distributionConfiguration);
        }
    }

    private static void copyJarTree(final URL source, final File target) throws IOException {
        final JarURLConnection jarConnection = (JarURLConnection) source.openConnection();
        final String prefix = jarConnection.getEntryName();
        final JarFile jarFile = jarConnection.getJarFile();
        for (final JarEntry jarEntry : Collections.list(jarFile.entries())) {
            final String entryName = jarEntry.getName();
            if (entryName.startsWith(prefix)) {
                if (!jarEntry.isDirectory()) {
                    final String fileName = StringUtils.removeStart(entryName, prefix);
                    final InputStream fileStream = jarFile.getInputStream(jarEntry);
                    try {
                        FileUtils.copyInputStreamToFile(fileStream, new File(target, fileName));
                    } finally {
                        fileStream.close();
                    }
                }
            }
        }
    }
}