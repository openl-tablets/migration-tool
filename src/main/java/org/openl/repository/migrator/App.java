package org.openl.repository.migrator;

import org.openl.repository.migrator.properties.PropertiesReader;
import org.openl.repository.migrator.properties.RepositoryProperties;
import org.openl.repository.migrator.repository.MappedFileData;
import org.openl.repository.migrator.repository.MappedRepository;
import org.openl.rules.repository.RepositoryInstatiator;
import org.openl.rules.repository.api.ChangesetType;
import org.openl.rules.repository.api.FileChange;
import org.openl.rules.repository.api.FileData;
import org.openl.rules.repository.api.FileItem;
import org.openl.rules.repository.api.FolderRepository;
import org.openl.rules.repository.api.Repository;
import org.openl.rules.repository.exceptions.RRepositoryException;
import org.openl.rules.repository.folder.FileChangesFromZip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.openl.repository.migrator.properties.RepositoryProperties.REPOSITORY_PREFIX;
import static org.openl.repository.migrator.utils.FileDataUtils.copyInfoWithoutVersion;
import static org.openl.repository.migrator.utils.FileDataUtils.getCopiedFileData;
import static org.openl.repository.migrator.utils.FileDataUtils.getNewName;
import static org.openl.repository.migrator.utils.FileDataUtils.writeFile;

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
        List<FileItem> resultList;
        for (FileData currentData : currentProjectsList) {
            // no need to copy deleted projects
            if (currentData.isDeleted()) {
                continue;
            }
            String projectName = currentData.getName();
            logger.info("Migrating project: {}", projectName);
            SortedSet<FileItem> sortedHistory = initializeSetForFileItem();
            List<FileData> allFileData = source.listHistory(projectName);
            for (FileData tempData : allFileData) {
                FileItem item = source.readHistory(tempData.getName(), tempData.getVersion());
                FileItem copy = new FileItem(getCopiedFileData(item.getData()), item.getStream());
                sortedHistory.add(copy);
            }
            if (target.supports().folders()) {
                for (FileItem fileItem : sortedHistory) {
                    FileData data = fileItem.getData();
                    try (ZipInputStream zipStream = new ZipInputStream(fileItem.getStream())) {
                        FileChangesFromZip filesInArchive = new FileChangesFromZip(zipStream, fileItem.getData().getName());
                        FileData folderToData;
                        if (!TARGET_USES_FLAT_PROJECTS) {
                            folderToData = createMappedFileData(BASE_PATH_TO, data);
                        } else {
                            folderToData = copyInfoWithoutVersion(data);
                        }
                        folderToData.setVersion(null);
                        ((FolderRepository) target).save(folderToData, filesInArchive, ChangesetType.FULL);
                    } catch (Exception e) {
                        logger.error("There was an error on saving the file " + data.getName(), e);
                    }
                }
            } else {
                resultList = new ArrayList<>(sortedHistory);
                target.save(resultList);
                for (FileItem fileItem : resultList) {
                    fileItem.getStream().close();
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
            SortedSet<FileData> foldersSortedByModifiedTime = initializeSetForFileDataFromGit();
            foldersSortedByModifiedTime.addAll(foldersList);

            List<FileData> filesOfVersion;
            List<FileChange> fileItemsOfTheVersion;
            for (FileData folderState : foldersSortedByModifiedTime) {
                filesOfVersion = folderRepo.listFiles(modifyProjectName(folderState.getName()), folderState.getVersion());
                fileItemsOfTheVersion = getFileItemsOfVersion(folderRepo, filesOfVersion);
                FileData copiedFolderData;
                if (!TARGET_USES_FLAT_PROJECTS) {
                    copiedFolderData = createMappedFileData(BASE_PATH_FROM, folderState);
                } else {
                    copiedFolderData = copyInfoWithoutVersion(folderState);
                }
                if (target.supports().folders()) {
                    ((FolderRepository) target).save(copiedFolderData, fileItemsOfTheVersion, ChangesetType.FULL);
                    for (FileChange fileItem : fileItemsOfTheVersion) {
                        fileItem.getStream().close();
                    }
                } else {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (ZipOutputStream zipOutputStream = new ZipOutputStream(out)) {
                        for (FileChange file : fileItemsOfTheVersion) {
                            writeFile(zipOutputStream, file, projectName);
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

    private static List<FileChange> getFileItemsOfVersion(FolderRepository folderRepo, List<FileData> projectFilesWithGivenVersion) throws IOException {
        List<FileChange> fileItemsOfTheVersion = new ArrayList<>();
        for (FileData fileData : projectFilesWithGivenVersion) {
            FileItem fi = folderRepo.readHistory(modifyProjectName(fileData.getName()), fileData.getVersion());
            FileChange copyOfFi = new FileChange(getCopiedFileData(fi.getData()), fi.getStream());
            fileItemsOfTheVersion.add(copyOfFi);
        }
        return fileItemsOfTheVersion;
    }

    private static Comparator<Date> nullSafeDateComparator = Comparator
            .nullsFirst(Date::compareTo);

    private static Comparator<String> nullSafeStringComparator = Comparator
            .nullsFirst(String::compareToIgnoreCase);

    private static SortedSet<FileData> initializeSetForFileDataFromGit() {
        return new TreeSet<>(Comparator.comparing(FileData::getModifiedAt, nullSafeDateComparator)
                .thenComparing(FileData::getVersion, nullSafeStringComparator));
    }

    private static SortedSet<FileItem> initializeSetForFileItem() {
        return new TreeSet<>(Comparator.comparing(x -> x.getData().getModifiedAt(), nullSafeDateComparator));
    }

    private static FileData createMappedFileData(String prefix, FileData data) {
        FileData folderToData;
        String name = data.getName();
        String projectName = name.substring(prefix.length());
        String path = getNewName(name);
        folderToData = new MappedFileData(path, projectName);
        folderToData.setAuthor(data.getAuthor());
        folderToData.setComment(data.getComment());
        folderToData.setDeleted(data.isDeleted());
        folderToData.setModifiedAt(data.getModifiedAt());
        return folderToData;
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
