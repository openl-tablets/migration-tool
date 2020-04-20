package org.openl.repository.migrator;

import org.openl.repository.migrator.properties.PropertiesReader;
import org.openl.repository.migrator.properties.RepositoryProperties;
import org.openl.repository.migrator.repository.FileMappingData;
import org.openl.repository.migrator.repository.MappedRepository;
import org.openl.rules.repository.RepositoryInstatiator;
import org.openl.rules.repository.api.ChangesetType;
import org.openl.rules.repository.api.FileData;
import org.openl.rules.repository.api.FileItem;
import org.openl.rules.repository.api.FolderItem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.openl.repository.migrator.properties.RepositoryProperties.REPOSITORY_PREFIX;
import static org.openl.repository.migrator.utils.FileDataUtils.copyInfoWithoutVersion;
import static org.openl.repository.migrator.utils.FileDataUtils.getCopiedFileData;
import static org.openl.repository.migrator.utils.FileDataUtils.getNewName;
import static org.openl.repository.migrator.utils.FileDataUtils.initializeSetForFileItems;
import static org.openl.repository.migrator.utils.FileDataUtils.initializeSetForFolderItems;
import static org.openl.repository.migrator.utils.FileDataUtils.writeFile;

/**
 * Hello world!
 */
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
        Repository source = getRepository(SOURCE);
        Repository target = getRepository(TARGET);

        Map<String, SortedSet<FolderItem>> projectWithAllFoldersHistory = new HashMap<>();

        Map<String, SortedSet<FileItem>> projectWithAllFileItems = new HashMap<>();

        boolean sourceSupportsFolders = source.supports().folders();
        boolean targetSupportsFolders = target.supports().folders();

        try {
            if (sourceSupportsFolders) {
                FolderRepository folderRepo = SOURCE_USES_FLAT_PROJECTS ? (FolderRepository) source : createMappedRepository(source, SOURCE);
                List<FileData> folders = folderRepo.listFolders(BASE_PATH_FROM);
                for (FileData folder : folders) {
                    // no need to copy deleted projects
                    if (folder.isDeleted()) {
                        continue;
                    }
                    List<FileData> foldersList;
                    String projectName = folder.getName();
                    projectName = modifyProjectName(projectName);
                    //all versions of folder
                    foldersList = folderRepo.listHistory(projectName);
                    for (FileData folderState : foldersList) {
                        //all files related to the folder and given version
                        List<FileData> projectFilesWithGivenVersion = folderRepo.listFiles(modifyProjectName(folderState.getName()), folderState.getVersion());
                        List<FileItem> fileItemsOfTheVersion = new ArrayList<>();
                        for (FileData fileData : projectFilesWithGivenVersion) {
                            FileItem fi = folderRepo.readHistory(modifyProjectName(fileData.getName()), fileData.getVersion());
                            FileItem copyOfFi = new FileItem(getCopiedFileData(fi.getData()), fi.getStream());
                            fileItemsOfTheVersion.add(copyOfFi);
                        }
                        FileData copiedFileData = getCopiedFileData(folderState);
                        if (!TARGET_USES_FLAT_PROJECTS) {
                            copiedFileData.addAdditionalData(new FileMappingData(folderState.getName().substring(BASE_PATH_FROM.length())));
                        }
                        FolderItem folderWithVersion = new FolderItem(copiedFileData, fileItemsOfTheVersion);
                        if (projectWithAllFoldersHistory.containsKey(projectName)) {
                            SortedSet<FolderItem> folderItems = projectWithAllFoldersHistory.get(projectName);
                            folderItems.add(folderWithVersion);
                        } else {
                            SortedSet<FolderItem> init = initializeSetForFolderItems();
                            init.add(folderWithVersion);
                            projectWithAllFoldersHistory.put(projectName, init);
                        }
                    }
                }
            } else {
                collectFileItems(source, projectWithAllFileItems);
            }
        } catch (IOException e) {
            logger.error("Application can't read files from source repository.", e);
            System.exit(1);
        }

        try {
            if (sourceSupportsFolders) {
                //when source and target support folders both we can easily put all the folders as is
                if (targetSupportsFolders) {
                    FolderRepository targetFolderRepo = TARGET_USES_FLAT_PROJECTS ? (FolderRepository) target : createMappedRepository(target, TARGET);
                    for (Map.Entry<String, SortedSet<FolderItem>> stringTreeSetEntry : projectWithAllFoldersHistory.entrySet()) {
                        SortedSet<FolderItem> folderItems = stringTreeSetEntry.getValue();
                        for (FolderItem folderItem : folderItems) {
                            folderItem.getData().setVersion(null);
                        }
                        targetFolderRepo.save(new ArrayList<>(folderItems), ChangesetType.FULL);
                    }

                } else {
                    //for storing folders from git it's needed to archive all the versions of the path
                    for (Map.Entry<String, SortedSet<FolderItem>> stringTreeSetEntry : projectWithAllFoldersHistory.entrySet()) {
                        for (FolderItem folderItem : stringTreeSetEntry.getValue()) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            try (ZipOutputStream zipOutputStream = new ZipOutputStream(out)) {
                                for (FileItem file : folderItem.getFiles()) {
                                    writeFile(zipOutputStream, file, stringTreeSetEntry.getKey());
                                }
                                zipOutputStream.finish();
                                FileData copy = folderItem.getData();
                                copy.setSize(out.size());
                                target.save(copy, new ByteArrayInputStream(out.toByteArray()));

                            } catch (Exception e) {
                                logger.error("There was an error during saving the zip file " + folderItem.getData().getName(), e);
                            }
                        }
                    }
                }
            } else {
                //when source not supporting folders. We're taking from jdbc, aws, jcr
                //if we're saving to git
                if (targetSupportsFolders) {
                    FolderRepository targetFolderRepo = TARGET_USES_FLAT_PROJECTS ? (FolderRepository) target : createMappedRepository(target, TARGET);
                    storeDataToFileRepository(projectWithAllFileItems, targetFolderRepo);
                } else {
                    //saving target git, jcr, aws
                    for (Map.Entry<String, SortedSet<FileItem>> pathWithFiles : projectWithAllFileItems.entrySet()) {
                        SortedSet<FileItem> fileItems = pathWithFiles.getValue();
                        target.save(new ArrayList<>(fileItems));
                    }
                }

            }
        } catch (IOException e) {
            logger.error("There was an error during the saving the files to target.", e);
            System.exit(1);
        }

        logger.info("Migration was finished successfully.");

    }

    private static void storeDataToFileRepository(Map<String, SortedSet<FileItem>> projectWithAllFileItems,
                                                  FolderRepository targetFolderRepo) {
        for (Map.Entry<String, SortedSet<FileItem>> pathWithFiles : projectWithAllFileItems.entrySet()) {
            String pathTo = pathWithFiles.getKey();
            SortedSet<FileItem> fileItems = pathWithFiles.getValue();
            for (FileItem fileItem : fileItems) {
                try (ZipInputStream zipStream = new ZipInputStream(fileItem.getStream())) {
                    FileChangesFromZip filesInArchive = new FileChangesFromZip(zipStream, getNewName(pathTo));
                    FileData folderToData = copyInfoWithoutVersion(fileItem.getData());
                    if (!TARGET_USES_FLAT_PROJECTS) {
                        FileMappingData f = new FileMappingData(folderToData.getName().substring(BASE_PATH_TO.length()));
                        folderToData.addAdditionalData(f);
                    }
                    folderToData.setVersion(null);
                    targetFolderRepo.save(folderToData, filesInArchive, ChangesetType.FULL);
                } catch (Exception e) {
                    logger.error("There was an error on saving the file " + fileItem.getData().getName(), e);
                }
            }
        }
    }

    private static void collectFileItems(Repository source, Map<String, SortedSet<FileItem>> projectWithAllFileItems) throws IOException {
        List<FileData> fileDataList;
        fileDataList = source.list(BASE_PATH_FROM);
        for (FileData currentData : fileDataList) {
            // no need to copy deleted projects
            if (currentData.isDeleted()) {
                continue;
            }
            /*
               creating a map <projectName, sorted set of all the files related to the project>
             */
            String projectName = currentData.getName();
            List<FileData> allFileData = source.listHistory(projectName);
            for (FileData tempData : allFileData) {
                FileItem item = source.readHistory(tempData.getName(), tempData.getVersion());
                FileItem copy = new FileItem(getCopiedFileData(item.getData()), item.getStream());
                if (projectWithAllFileItems.containsKey(projectName)) {
                    SortedSet<FileItem> fileItems = projectWithAllFileItems.get(projectName);
                    fileItems.add(copy);
                } else {
                    SortedSet<FileItem> itemsSet = initializeSetForFileItems();
                    itemsSet.add(copy);
                    projectWithAllFileItems.put(projectName, itemsSet);
                }
            }
        }
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
            logger.error(String.format("No able to connect to '%s' repository.", settingsPrefix));
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
