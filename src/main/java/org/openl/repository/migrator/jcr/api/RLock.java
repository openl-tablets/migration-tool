package org.openl.repository.migrator.jcr.api;

import org.openl.rules.common.CommonUser;
import org.openl.rules.common.LockInfo;
import org.openl.rules.repository.exceptions.RRepositoryException;

import java.util.Date;

public interface RLock extends LockInfo {
    RLock NO_LOCK = new RLock() {
        @Override
        public Date getLockedAt() {
            return null;
        }

        @Override
        public CommonUser getLockedBy() {
            return null;
        }

        @Override
        public boolean isLocked() {
            return false;
        }

        @Override
        public void lock(CommonUser user) throws RRepositoryException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unlock(CommonUser user) throws RRepositoryException {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    Date getLockedAt();

    @Override
    CommonUser getLockedBy();

    @Override
    boolean isLocked();

    void lock(CommonUser user) throws RRepositoryException;

    void unlock(CommonUser user) throws RRepositoryException;
}
