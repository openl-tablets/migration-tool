package org.openl.repository.migrator.utils;

import org.openl.rules.repository.api.FileData;

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
        return name.replace(BASE_PATH_FROM, BASE_PATH_TO);
    }
}
