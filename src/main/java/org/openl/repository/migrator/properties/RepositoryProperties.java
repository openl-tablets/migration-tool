package org.openl.repository.migrator.properties;

import java.util.HashMap;
import java.util.Map;

public class RepositoryProperties {

    public static final String REPOSITORY_PREFIX = "repository.";

    private RepositoryProperties() {
    }

    public static Map<String, String> getRepositoryProperties(String name) {
        Map<String, String> params = new HashMap<>();
        params.put("uri", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".uri"));
        params.put("login", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".login"));
        params.put("password", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".password"));
        // AWS S3 specific
        params.put("bucketName", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".bucket-name"));
        params.put("regionName", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".region-name"));
        params.put("accessKey", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".access-key"));
        params.put("secretKey", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".secret-key"));
        // Git specific
        params.put("localRepositoryPath", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".local-repository-path"));
        params.put("branch", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".branch"));
        params.put("tagPrefix", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".tag-prefix"));
        params.put("commentTemplate", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".comment-template"));
        params.put("connection-timeout", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".connection-timeout"));
        // AWS S3 and Git specific
        params.put("listener-timer-period", PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + name + ".listener-timer-period"));
        return params;
    }
}
