package org.openl.repository.migrator.repository;

import org.openl.rules.repository.api.FileItem;

import java.util.Arrays;
import java.util.Iterator;

public class CompositeFileChanges implements Iterable<FileItem> {
    private final Iterable<FileItem> first;
    private final Iterable<FileItem> second;

    public CompositeFileChanges(Iterable<FileItem> first, FileItem... second) {
        this(first, Arrays.asList(second));
    }

    public CompositeFileChanges(Iterable<FileItem> first, Iterable<FileItem> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public Iterator<FileItem> iterator() {
        return new Iterator<FileItem>() {
            private Iterator<FileItem> firstIterator = first.iterator();
            private Iterator<FileItem> secondIterator = second.iterator();

            @Override
            public boolean hasNext() {
                if (firstIterator != null) {
                    if (firstIterator.hasNext()) {
                        return true;
                    } else {
                        firstIterator = null;
                    }
                }

                return secondIterator.hasNext();
            }

            @Override
            public FileItem next() {
                return firstIterator != null ? firstIterator.next() : secondIterator.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Remove is not supported");
            }
        };
    }
}
