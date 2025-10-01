package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@NotNullByDefault
public abstract class DefaultFileSystem extends BaseFileSystem {

    private final BaseFileSystemProvider provider;
    private final DefaultFileStore fileStore;
    private boolean isOpen;

    public DefaultFileSystem(BaseFileSystemProvider provider) {
        this.provider = provider;
        this.fileStore = new DefaultFileStore();
        this.isOpen = true;
    }

    @Override
    public BaseFileSystemProvider provider() {
        return this.provider;
    }

    @Override
    public boolean isOpen() {
        return this.isOpen;
    }

    @Override
    public void close() throws IOException {
        this.provider.unregisterFileSystem(this);
        this.isOpen = false;
    }

    @Override
    public boolean isReadOnly() {
        return this.provider.isReadOnly();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(this.getRoot());
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections.singletonList(this.fileStore);
    }

    @Override
    public FileStore getFileStore(Path path) {
        return this.fileStore;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override
    public Path getPath(String first, String... more) {
        String[] parts = new String[more.length + 1];
        parts[0] = first;
        System.arraycopy(more, 0, parts, 1, more.length);
        return new BasePath(this, parts);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0) throw new IllegalArgumentException();
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos + 1);
        String pattern;
        if (syntax.equalsIgnoreCase("glob")) {
            pattern = GlobToRegex.toRegexPattern(input, this);
        } else if (syntax.equalsIgnoreCase("regex")) {
            pattern = input;
        } else {
            throw new UnsupportedOperationException("Syntax '" + syntax + "' not recognized");
        }
        Predicate<String> test = Pattern.compile(pattern).asMatchPredicate();
        return path -> test.test(path.toString());
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Path getRoot() {
        return new BasePath(this, this.getSeparator());
    }

    @Override
    public Path toRealPath(Path path) {
        return path;
    }

    @NotNullByDefault
    private class DefaultFileStore extends FileStore {

        @Override
        public String name() {
            return DefaultFileSystem.this.getRoot().toString();
        }

        @Override
        public String type() {
            return DefaultFileSystem.this.provider().getScheme();
        }

        @Override
        public boolean isReadOnly() {
            return DefaultFileSystem.this.isReadOnly();
        }

        @Override
        public long getTotalSpace() {
            return DefaultFileSystem.this.isReadOnly() ? 0 : Long.MAX_VALUE;
        }

        @Override
        public long getUsableSpace() {
            return DefaultFileSystem.this.isReadOnly() ? 0 : Long.MAX_VALUE;
        }

        @Override
        public long getUnallocatedSpace() {
            return DefaultFileSystem.this.isReadOnly() ? 0 : Long.MAX_VALUE;
        }

        @Override
        public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
            return false;
        }

        @Override
        public boolean supportsFileAttributeView(String name) {
            return DefaultFileSystem.this.supportedFileAttributeViews().contains(name);
        }

        @Override
        public Object getAttribute(String attribute) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V extends FileStoreAttributeView> @Nullable V getFileStoreAttributeView(Class<V> type) {
            return null;
        }
    }

    @NotNullByDefault
    @SuppressWarnings("ClassCanBeRecord")
    public static class SimpleDirectoryStream implements DirectoryStream<Path> {

        private final Iterable<Path> paths;
        private final @Nullable DirectoryStream.Filter<? super Path> filter;

        public SimpleDirectoryStream(Iterable<Path> paths, @Nullable DirectoryStream.Filter<? super Path> filter) {
            this.paths = paths;
            this.filter = filter;
        }

        @Override
        public Iterator<Path> iterator() {
            return new FilteringIterator(this.paths.iterator(), this.filter);
        }

        @Override
        public void close() {
            //
        }
    }

    @NotNullByDefault
    private static class FilteringIterator implements Iterator<Path> {
        
        private final Iterator<Path> itr;
        private final @Nullable DirectoryStream.Filter<? super Path> filter;
        private @Nullable Path next;

        private FilteringIterator(Iterator<Path> itr, @Nullable DirectoryStream.Filter<? super Path> filter) {
            this.itr = itr;
            this.filter = filter;
        }

        private boolean setupNext() {
            while (this.next == null) {
                if (!this.itr.hasNext()) return false;
                Path candidate = Objects.requireNonNull(this.itr.next());
                try {
                    if (this.filter == null || this.filter.accept(candidate)) {
                        this.next = candidate;
                    }
                } catch (IOException e) {
                    throw new DirectoryIteratorException(e);
                }
            }
            return true;
        }
        
        @Override
        public boolean hasNext() {
            return this.setupNext();
        }

        @Override
        public Path next() {
            if (this.setupNext()) {
                Path next = Objects.requireNonNull(this.next);
                this.next = null;
                return next;
            } else {
                throw new NoSuchElementException();
            }
        }
    }
}
