package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.util.Objects;

@NotNullByDefault
public abstract class DefaultFileSystemProvider extends BaseFileSystemProvider {

    @Override
    public boolean isSameFile(Path firstPath, Path secondPath) throws IOException {
        return Objects.equals(firstPath.toRealPath(), secondPath.toRealPath());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        if (path instanceof BasePath bp) return bp.getFileSystem().getFileStore(path);
        throw new IOException("Wrong type of path.");
    }
}
