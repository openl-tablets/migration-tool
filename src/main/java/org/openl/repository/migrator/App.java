package org.openl.repository.migrator;

import org.openl.repository.migrator.properties.PropertiesReader;
import org.openl.repository.migrator.properties.RepositoryProperties;
import org.openl.repository.migrator.repository.FileMappingData;
import org.openl.repository.migrator.repository.MappedRepository;
import org.openl.repository.migrator.utils.FileDataUtils;
import org.openl.rules.repository.RepositoryInstatiator;
import org.openl.rules.repository.api.ChangesetType;
import org.openl.rules.repository.api.FileData;
import org.openl.rules.repository.api.FileItem;
import org.openl.rules.repository.api.FolderRepository;
import org.openl.rules.repository.api.Repository;
import org.openl.rules.repository.exceptions.RRepositoryException;
import org.openl.rules.repository.folder.FileChangesFromZip;
import org.openl.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.openl.repository.migrator.properties.RepositoryProperties.REPOSITORY_PREFIX;
import static org.openl.repository.migrator.utils.FileDataUtils.copyInfoWithoutVersion;
import static org.openl.repository.migrator.utils.FileDataUtils.getCopiedFileData;

public class App {

    public static final String SOURCE = "source";
    public static final String TARGET = "target";

    public static final String BASE_PATH_FROM = PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + SOURCE + ".base.path");
    public static final String BASE_PATH_TO = PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + TARGET + ".base.path");

    public static final boolean SOURCE_USES_FLAT_PROJECTS = Boolean.parseBoolean(PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + SOURCE + ".folder-structure.flat", "true"));
    public static final boolean TARGET_USES_FLAT_PROJECTS = Boolean.parseBoolean(PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + TARGET + ".folder-structure.flat", "true"));

    private static final Logger logger = LoggerFactory.getLogger(App.class);


    public static void main(String[] args) {
        logger.info("starting...");
        long startTime = System.nanoTime();

        Repository source = getRepository(SOURCE);
        Repository target = getRepository(TARGET);

        if (target.supports().folders()) {
            target = TARGET_USES_FLAT_PROJECTS ? (FolderRepository) target : createMappedRepository(target, TARGET);
        }

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
                        if (!TARGET_USES_FLAT_PROJECTS) {
                            FileMappingData f = new FileMappingData(copiedFileData.getName().substring(BASE_PATH_TO.length()));
                            copiedFileData.addAdditionalData(f);
                        }
                        copiedFileData.setVersion(null);
                        ((FolderRepository) target).save(copiedFileData, filesInArchive, ChangesetType.FULL);
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
        FolderRepository folderRepo = SOURCE_USES_FLAT_PROJECTS ? (FolderRepository) source : createMappedRepository(source, SOURCE);
        List<FileData> folders = folderRepo.listFolders(BASE_PATH_FROM);
        List<FileData> foldersList;
        for (FileData folder : folders) {
            if (folder.isDeleted()) {
                continue;
            }
            String projectName = folder.getName();
            logger.info("Migrating project: {}", projectName);
            projectName = modifyProjectName(projectName);
            //all versions of folder
            foldersList = folderRepo.listHistory(projectName);
            //sorted by modified time folders
            SortedSet<FileData> foldersSortedByModifiedTime = initializeSetForFileData();
            foldersSortedByModifiedTime.addAll(foldersList);

            List<FileData> filesOfVersion;
            List<FileItem> fileItemsOfTheVersion;
            for (FileData folderState : foldersSortedByModifiedTime) {
                String version = folderState.getVersion();
                filesOfVersion = folderRepo.listFiles(modifyProjectName(folderState.getName()), version);
                fileItemsOfTheVersion = getFileItemsOfVersion(folderRepo, filesOfVersion, version);
                FileData copiedFolderData = copyInfoWithoutVersion(folderState);
                if (!TARGET_USES_FLAT_PROJECTS) {
                    copiedFolderData.addAdditionalData(new FileMappingData(folderState.getName().substring(BASE_PATH_FROM.length())));
                }
                if (target.supports().folders()) {
                    try {
                        ((FolderRepository) target).save(copiedFolderData, fileItemsOfTheVersion, ChangesetType.FULL);
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
                            String name = file.getData().getName().substring(FileDataUtils.getNewName(projectName).length());
                            try (InputStream fileStream = file.getStream()) {
                                zipOutputStream.putNextEntry(new ZipEntry(name));
                                IOUtils.copy(fileStream, zipOutputStream);
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

    private static List<FileItem> getFileItemsOfVersion(FolderRepository folderRepo, List<FileData> projectFilesWithGivenVersion, String version) throws IOException {
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
            Map<String, String> repoProperties = RepositoryProperties.getRepositoryProperties(settingsPrefix);
            return RepositoryInstatiator.newRepository(PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + settingsPrefix + ".factory"), repoProperties);
        } catch (Exception e) {
            logger.error(String.format("No able to connect to '%s' repository.", settingsPrefix), e);
            System.exit(1);
        }
        return r;
    }

    private static MappedRepository createMappedRepository(Repository repo, String settingsPrefix) {
        MappedRepository mappedRepository = new MappedRepository();
        mappedRepository.setDelegate((FolderRepository) repo);
        mappedRepository.setBaseFolder(settingsPrefix.equals(SOURCE) ? BASE_PATH_FROM : BASE_PATH_TO);
        mappedRepository.setConfigFile(PropertiesReader.PROPERTIES.getProperty(REPOSITORY_PREFIX + settingsPrefix + ".folder-structure.configuration"));
        try {
            mappedRepository.initialize();
            return mappedRepository;
        } catch (RRepositoryException e) {
            logger.error("Can't create mapped repository.", e);
            System.exit(1);
        }
        return mappedRepository;
    }

}
