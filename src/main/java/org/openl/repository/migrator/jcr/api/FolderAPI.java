package org.openl.repository.migrator.jcr.api;

import org.openl.rules.common.CommonVersion;
import org.openl.rules.common.ProjectException;

import java.io.InputStream;
import java.util.Collection;

public interface FolderAPI extends ArtefactAPI {
    ArtefactAPI getArtefact(String name) throws ProjectException;

    boolean hasArtefact(String name);

    FolderAPI addFolder(String name) throws ProjectException;

    ResourceAPI addResource(String name, InputStream content) throws ProjectException;

    Collection<? extends ArtefactAPI> getArtefacts();

    @Override
    FolderAPI getVersion(CommonVersion version) throws ProjectException;
}
