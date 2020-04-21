package org.openl.repository.migrator.utils;

import org.openl.rules.repository.api.FileChange;
import org.openl.rules.repository.api.FileData;
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
        return copyData;
    }


    public static String getNewName(String name) {
        if (!name.contains(BASE_PATH_TO)) {
            return BASE_PATH_TO + name.substring(BASE_PATH_FROM.length());
        } else {
            return name;
        }
    }

    public static void writeFile(ZipOutputStream zipOutputStream, FileChange fd, String pathTo) throws IOException {
        String name = fd.getData().getName().substring(getNewName(pathTo).length());
        zipOutputStream.putNextEntry(new ZipEntry(name));
        InputStream content = fd.getStream();
        IOUtils.copy(content, zipOutputStream);
        content.close();
        zipOutputStream.closeEntry();
    }


}
