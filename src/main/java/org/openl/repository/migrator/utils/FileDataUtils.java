package org.openl.repository.migrator.utils;

import org.openl.repository.migrator.repository.FileMappingData;
import org.openl.rules.repository.api.FileData;
import org.openl.rules.repository.api.FileItem;
import org.openl.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.openl.repository.migrator.App.BASE_PATH_FROM;
import static org.openl.repository.migrator.App.BASE_PATH_TO;

public class FileDataUtils {

    private FileDataUtils() {
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
        copyData.setAuthor(data.getAuthor());
        copyData.setModifiedAt(data.getModifiedAt());
        copyData.setDeleted(data.isDeleted());
        copyData.setSize(data.getSize());
        FileMappingData additionalData = data.getAdditionalData(FileMappingData.class);
        if (additionalData != null) {
            FileMappingData newData = new FileMappingData(getNewName(additionalData.getInternalPath()));
            copyData.addAdditionalData(newData);
        }
        return copyData;
    }


    public static String getNewName(String name) {
        return name.replace(BASE_PATH_FROM, BASE_PATH_TO);
    }

    public static void writeFile(ZipOutputStream zipOutputStream, FileItem fd, String pathTo) throws IOException {
        String name = fd.getData().getName().substring(getNewName(pathTo).length());
        zipOutputStream.putNextEntry(new ZipEntry(name));
        InputStream content = fd.getStream();
        IOUtils.copy(content, zipOutputStream);
        content.close();
        zipOutputStream.closeEntry();
    }


}
