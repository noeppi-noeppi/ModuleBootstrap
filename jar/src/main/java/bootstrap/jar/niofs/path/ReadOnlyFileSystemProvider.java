package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.*;
import java.util.Map;

@NotNullByDefault
public abstract class ReadOnlyFileSystemProvider extends DefaultFileSystemProvider {

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void delete(Path path) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public <V extends FileAttributeView> @Nullable V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (type == BasicFileAttributeView.class) {
            @SuppressWarnings("unchecked")
            V view = (V) new SimpleAttributesView(path, options);
            return view;
        }
        return null;
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    private class SimpleAttributesView implements BasicFileAttributeView {

        private final Path path;
        private final LinkOption[] linkOptions;

        private SimpleAttributesView(Path path, LinkOption[] linkOptions) {
            this.path = path;
            this.linkOptions = linkOptions;
        }

        @Override
        public String name() {
            return "basic";
        }

        @Override
        public BasicFileAttributes readAttributes() throws IOException {
            return ReadOnlyFileSystemProvider.this.readAttributes(this.path, BasicFileAttributes.class, this.linkOptions);
        }

        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
            throw new ReadOnlyFileSystemException();
        }
    }
}
