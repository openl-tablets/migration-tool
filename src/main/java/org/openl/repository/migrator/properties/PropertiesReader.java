package org.openl.repository.migrator.properties;

import org.openl.repository.migrator.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesReader {

    private static final Logger logger = LoggerFactory.getLogger(PropertiesReader.class);

    public static final Properties PROPERTIES;

    public static final String APPLICATION_PROPERTIES_LOCATION = "/application.properties";

    static {
        PROPERTIES = new Properties();
        try {
            InputStream propertiesOutside = getOutsideProperties();
            if (propertiesOutside != null) {
                loadProperties(propertiesOutside);
            } else {
                InputStream defaultProperties = getDefaultProperties(APPLICATION_PROPERTIES_LOCATION);
                loadProperties(defaultProperties);
            }
        } catch (Exception e) {
            logger.error("Properties file not found.");
        }
    }

    private static InputStream getOutsideProperties() {
        File path = new File(App.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        try {
            return new FileInputStream(path.getParent() + PropertiesReader.APPLICATION_PROPERTIES_LOCATION);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private PropertiesReader() {
    }

    private static void loadProperties(InputStream stream) {
        try {
            PROPERTIES.load(stream);
            logger.info("properties are loaded...");
        } catch (Exception e) {
            logger.error("Properties weren't loaded.");
        } finally {
            closePropertiesFile(stream);
        }
    }

    private static void closePropertiesFile(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ioe) {
            logger.error("Unable to close the properties file.");
        }
    }

    public static InputStream getDefaultProperties(String resource) {
        String stripped = resource.startsWith("/")
                ? resource.substring(1)
                : resource;
        InputStream stream = null;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            stream = classLoader.getResourceAsStream(stripped);
        }
        if (stream == null) {
            stream = App.class.getResourceAsStream(resource);
        }
        if (stream == null) {
            stream = App.class.getClassLoader().getResourceAsStream(stripped);
        }
        if (stream == null) {
            throw new IllegalStateException(resource + " not found");
        }
        return stream;
    }
}
