package bootstrap.jar.niofs.empty;

import bootstrap.jar.niofs.path.BasePath;
import bootstrap.jar.niofs.path.DefaultFileSystem;
import bootstrap.jar.niofs.path.ReadOnlyFileSystemProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@NotNullByDefault
public class EmptyFileSystemProvider extends ReadOnlyFileSystemProvider  {

    private final EmptyFileSystem fs;

    public EmptyFileSystemProvider() {
        this.fs = new EmptyFileSystem(this);
    }

    @Override
    public String getScheme() {
        return "empty";
    }

    @Override
    public Path getPath(URI uri) {
        if (!Objects.equals(this.getScheme(), uri.getScheme())) {
            throw new IllegalArgumentException("Wrong URI scheme, expected " + this.getScheme() + ", got " + uri.getScheme());
        }
        return this.fs.getPath(uri.getPath());
    }

    @Override
    public URI toURI(BasePath path) throws URISyntaxException {
        return new URI("empty", path.toString(), null);
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        throw new FileSystemAlreadyExistsException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        if (!Objects.equals(this.getScheme(), uri.getScheme())) {
            throw new IllegalArgumentException("Wrong URI scheme, expected " + this.getScheme() + ", got " + uri.getScheme());
        }
        return this.fs;
    }

    @Override
    public void unregisterFileSystem(FileSystem fs) {
        //
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND) || options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW)) {
            throw new ReadOnlyFileSystemException();
        } else {
            throw new NoSuchFileException(path.toUri().toString());
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (this.fs.getRoot().equals(dir.toAbsolutePath())) {
            return new DefaultFileSystem.SimpleDirectoryStream(Collections.emptyList(), filter);
        } else {
            throw new NoSuchFileException(dir.toUri().toString());
        }
    }

    @Override
    public boolean exists(Path path, LinkOption... options) {
        return this.fs.getRoot().equals(path.toAbsolutePath());
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        for (AccessMode mode : modes) {
            if (mode == AccessMode.WRITE || mode == AccessMode.EXECUTE) {
                throw new AccessDeniedException(path.toString());
            }
        }
        if (!this.fs.getRoot().equals(path.toAbsolutePath())) {
            throw new NoSuchFileException(path.toUri().toString());
        }
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class) {
            if (!this.fs.getRoot().equals(path.toAbsolutePath())) {
                throw new NoSuchFileException(path.toUri().toString());
            } else {
                @SuppressWarnings("unchecked")
                A attributes = (A) RootAttributes.INSTANCE;
                return attributes;
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @NotNullByDefault
    private static class RootAttributes implements BasicFileAttributes {

        private static final RootAttributes INSTANCE = new RootAttributes();

        private RootAttributes() {}

        @Override
        public FileTime lastModifiedTime() {
            return FileTime.fromMillis(0);
        }

        @Override
        public FileTime lastAccessTime() {
            return FileTime.fromMillis(0);
        }

        @Override
        public FileTime creationTime() {
            return FileTime.fromMillis(0);
        }

        @Override
        public boolean isRegularFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return 0;
        }

        @Override
        public @Nullable Object fileKey() {
            return null;
        }
    }
}
