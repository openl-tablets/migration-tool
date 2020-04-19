package org.openl.repository.migrator.jackrabbit;

import org.apache.jackrabbit.jcr2spi.RepositoryImpl;
import org.openl.repository.migrator.jcr.factory.AbstractJcrRepositoryFactory;
import org.openl.rules.repository.exceptions.RRepositoryException;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeTypeManager;

/**
 *
 * @author PUdalau
 */
public class WebDavRepositoryFactory extends AbstractJcrRepositoryFactory {

    /** {@inheritDoc} */
    @Override
    public void initialize() throws RRepositoryException {
        try {
            Repository repository;
            String webDavUrl = this.uri;
            try {
                // FIXME Does not work on the secure mode
                repository = RepositoryImpl.create(new DavexRepositoryConfigImpl(webDavUrl));
                // repository = JcrUtils.getRepository(confWebdavUrl.getValue());
            } catch (Exception e) {
                throw new RepositoryException(e);
            }

            setRepository(repository);
        } catch (RepositoryException e) {
            throw new RRepositoryException("Failed to initialize JCR: " + e.getMessage(), e);
        }
        super.initialize();
    }

    /** {@inheritDoc} */
    @Override
    protected void initNodeTypes(NodeTypeManager ntm) throws RepositoryException {
        throw new RepositoryException(
            "Cannot initialize node types via WebDav." + "\nPlease, add OpenL node types definition manually or via command line tool.");
    }
}
