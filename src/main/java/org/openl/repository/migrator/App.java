package org.openl.repository.migrator;

import org.openl.rules.repository.RepositoryInstatiator;
import org.openl.rules.repository.api.*;
import org.openl.rules.repository.folder.FileChangesFromZip;
import org.openl.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class App {

    public static final String REPOSITORY_PREFIX = "repository.";

    public static final String SOURCE = "source";
    public static final String TARGET = "target";

    private static final Properties PROPERTIES = new Properties();
    private static final Properties USERS = new Properties();
    private static String EMAIL_TEMPLATE;
    private static String DISPLAYNAME_TEMPLATE;
    private static String BASE_PATH_FROM;
    private static String BASE_PATH_TO;

    private static final Logger logger = LoggerFactory.getLogger(App.class);


    public static void main(String[] args) throws Exception{
        logger.info("starting...");
        long startTime = System.nanoTime();

        String propFilePath = "application.properties";
        if (args != null && args.length>0) {
            propFilePath = args[0];
        }

        try(var is = getResourceInputStream(propFilePath)) {
            PROPERTIES.load(is);
            logger.info("'{}' properties have been loaded.", propFilePath);
        }

        BASE_PATH_FROM = PROPERTIES.getProperty(REPOSITORY_PREFIX + SOURCE + ".base.path");
        BASE_PATH_TO = PROPERTIES.getProperty(REPOSITORY_PREFIX + TARGET + ".base.path");
        EMAIL_TEMPLATE = PROPERTIES.getProperty("users.email", "{username}@example.com");
        DISPLAYNAME_TEMPLATE = PROPERTIES.getProperty("users.displayName", "{username}");
        var usersFile = PROPERTIES.getProperty("users.file");

        if (usersFile != null) {
            try(var is = getResourceInputStream(usersFile)) {
                USERS.load(is);
                logger.info("'{}' properties have been loaded.", usersFile);
            }
        }

        Repository source = getRepository(SOURCE);
        Repository target = getRepository(TARGET);

        boolean sourceSupportsFolders = source.supports().folders();

        try {
            if (sourceSupportsFolders) {
                migrateFoldersWithFiles(source, target);
            } else {
                migrateFiles(source, target);
            }
        } catch (Exception e) {
            logger.error("Error during migration", e);
            System.exit(1);
        }

        long endTime = System.nanoTime();
        long executionTime = (endTime - startTime) / 1000000;

        String executionTimeMessage = String.format("%d min, %d sec",
                TimeUnit.MILLISECONDS.toMinutes(executionTime),
                TimeUnit.MILLISECONDS.toSeconds(executionTime) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(executionTime))
        );
        logger.info("Migration was finished in {} .", executionTimeMessage);

    }

    private static InputStream getResourceInputStream(String resource) {
        try {
            return new FileInputStream(resource);
        } catch (FileNotFoundException ignore) {
            // ignore
        }

        try {
            File path = new File(App.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            return new FileInputStream(new File(path.getParent(), resource));
        } catch (FileNotFoundException ignore) {
            // ignore
        }

        InputStream stream = null;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            stream = classLoader.getResourceAsStream(resource);
        }
        if (stream == null) {
            stream = App.class.getResourceAsStream(resource);
        }
        if (stream == null) {
            stream = App.class.getClassLoader().getResourceAsStream(resource);
        }
        if (stream == null) {
            logger.error(resource + " properties file not found.");
            System.exit(1);
        }
        return stream;
    }

    private static void migrateFiles(Repository source, Repository target) throws IOException {
        List<FileData> currentProjectsList = source.list(BASE_PATH_FROM);
        for (FileData currentData : currentProjectsList) {
            // no need to copy deleted projects
            if (currentData.isDeleted()) {
                continue;
            }
            String projectName = currentData.getName();
            logger.info("Migrating project: {}", projectName);
            SortedSet<FileData> sortedHistory = initializeSetForFileData();
            List<FileData> allFileData = source.listHistory(projectName);
            sortedHistory.addAll(allFileData);
            for (FileData currentFileData : sortedHistory) {
                String originalName = currentFileData.getName();
                String originalVersion = currentFileData.getVersion();
                FileItem item = source.readHistory(originalName, originalVersion);
                InputStream fileStream = item.getStream();
                FileData copiedFileData = getCopiedFileData(item.getData());
                if (target.supports().folders()) {
                    try (ZipInputStream zipStream = new ZipInputStream(fileStream)) {
                        FileChangesFromZip filesInArchive = new FileChangesFromZip(zipStream, copiedFileData.getName());
                        FileData folderToData;
                        folderToData = copyInfoWithoutVersion(copiedFileData);
                        folderToData.setVersion(null);
                        target.save(folderToData, filesInArchive, ChangesetType.FULL);
                    } catch (Exception e) {
                        logger.error("There was an error on saving the file " + originalName, e);
                    }
                } else {
                    try {
                        target.save(copiedFileData, fileStream);
                    } catch (Exception e) {
                        logger.error("There was an error on saving the file " + originalName, e);
                    } finally {
                        IOUtils.closeQuietly(fileStream);
                    }
                }
            }
        }
    }

    private static void migrateFoldersWithFiles(Repository source, Repository target) throws IOException {
        List<FileData> folders = source.listFolders(BASE_PATH_FROM);
        List<FileData> foldersList;
        for (FileData folder : folders) {
            if (folder.isDeleted()) {
                continue;
            }
            String projectName = folder.getName();
            logger.info("Migrating project: {}", projectName);
            projectName = modifyProjectName(projectName);
            //all versions of folder
            foldersList = source.listHistory(projectName);
            //sorted by modified time folders
            SortedSet<FileData> foldersSortedByModifiedTime = initializeSetForFileData();
            foldersSortedByModifiedTime.addAll(foldersList);

            List<FileData> filesOfVersion;
            List<FileItem> fileItemsOfTheVersion;
            for (FileData folderState : foldersSortedByModifiedTime) {
                String version = folderState.getVersion();
                String name = folderState.getName();
                filesOfVersion = source.listFiles(modifyProjectName(name), version);
                fileItemsOfTheVersion = getFileItemsOfVersion(source, filesOfVersion, version);
                FileData copiedFolderData;
                copiedFolderData = copyInfoWithoutVersion(folderState);
                if (target.supports().folders()) {
                    try {
                        target.save(copiedFolderData, fileItemsOfTheVersion, ChangesetType.FULL);
                    } catch (Exception e) {
                        logger.error("There was an error on saving the version: " + version, e);
                    } finally {
                        for (FileItem fileItem : fileItemsOfTheVersion) {
                            IOUtils.closeQuietly(fileItem.getStream());
                        }
                    }
                } else {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (ZipOutputStream zipOutputStream = new ZipOutputStream(out)) {
                        for (FileItem file : fileItemsOfTheVersion) {
                            String fn = file.getData().getName().substring(getNewName(projectName).length());
                            try (InputStream fileStream = file.getStream()) {
                                zipOutputStream.putNextEntry(new ZipEntry(fn));
                                fileStream.transferTo(zipOutputStream);
                                zipOutputStream.closeEntry();
                            }
                        }
                        zipOutputStream.finish();
                        copiedFolderData.setSize(out.size());
                        target.save(copiedFolderData, new ByteArrayInputStream(out.toByteArray()));

                    } catch (Exception e) {
                        logger.error("There was an error during saving the zip file " + projectName, e);
                    }
                }
            }
        }
    }

    private static List<FileItem> getFileItemsOfVersion(Repository folderRepo, List<FileData> projectFilesWithGivenVersion, String version) throws IOException {
        List<FileItem> fileItemsOfTheVersion = new ArrayList<>();
        for (FileData fileData : projectFilesWithGivenVersion) {
            FileItem fi = folderRepo.readHistory(modifyProjectName(fileData.getName()), version);
            FileItem copyOfFi = new FileItem(getCopiedFileData(fi.getData()), fi.getStream());
            fileItemsOfTheVersion.add(copyOfFi);
        }
        return fileItemsOfTheVersion;
    }

    private static Comparator<Date> nullSafeDateComparator = Comparator
            .nullsFirst(Date::compareTo);

    private static Comparator<String> nullSafeStringComparator = Comparator
            .nullsFirst(String::compareToIgnoreCase);

    private static SortedSet<FileData> initializeSetForFileData() {
        return new TreeSet<>(Comparator.comparing(FileData::getModifiedAt, nullSafeDateComparator)
                .thenComparing(FileData::getVersion, nullSafeStringComparator));
    }

    private static String modifyProjectName(String projectName) {
        if (!projectName.isEmpty() && !projectName.endsWith("/")) {
            projectName += "/";
        }
        return projectName;
    }

    private static Repository getRepository(String settingsPrefix) {
        Repository r = null;
        try {
            return RepositoryInstatiator.newRepository(REPOSITORY_PREFIX + settingsPrefix, PROPERTIES::getProperty);
        } catch (Exception e) {
            logger.error(String.format("No able to connect to '%s' repository.", settingsPrefix), e);
            System.exit(1);
        }
        return r;
    }

    public static FileData getCopiedFileData(FileData data) {
        FileData copyData = copyInfoWithoutVersion(data);
        copyData.setVersion(data.getVersion());
        return copyData;
    }

    public static FileData copyInfoWithoutVersion(FileData data) {
        FileData copyData = new FileData();
        copyData.setName(getNewName(data.getName()));
        copyData.setComment(data.getComment());
        UserInfo author = data.getAuthor();
        copyData.setAuthor(map(author));
        copyData.setModifiedAt(data.getModifiedAt());
        copyData.setDeleted(data.isDeleted());
        copyData.setSize(data.getSize());
        return copyData;
    }

    private static UserInfo map(UserInfo author) {

        if (author.getEmail() != null) {
            return author;
        }
        String username = author.getUsername();
        String email = EMAIL_TEMPLATE.replace("{username}", username);
        String displayName = DISPLAYNAME_TEMPLATE.replace("{username}", username);
        String user = USERS.getProperty(username);
        if (user != null) {
            var parts = user.split(",", 2);
            email = parts[0].trim();
            displayName = parts.length > 1 ? parts[1].trim() : displayName;
        }

        return new UserInfo(username, email, displayName);
    }


    public static String getNewName(String name) {
        return name.replace(BASE_PATH_FROM, BASE_PATH_TO);
    }
}
