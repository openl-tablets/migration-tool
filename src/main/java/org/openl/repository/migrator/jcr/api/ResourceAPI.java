package org.openl.repository.migrator.jcr.api;

import org.openl.rules.common.ProjectException;

import java.io.InputStream;

public interface ResourceAPI extends ArtefactAPI {

    InputStream getContent() throws ProjectException;

    void setContent(InputStream inputStream) throws ProjectException;

    long getSize();

}
