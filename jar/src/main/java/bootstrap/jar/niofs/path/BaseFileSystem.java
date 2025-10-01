package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;

@NotNullByDefault
public abstract class BaseFileSystem extends FileSystem {

    public abstract BaseFileSystemProvider provider();
    public abstract String getSeparator();
    public abstract Path getRoot();
    public abstract FileStore getFileStore(Path path);
    public abstract Path toRealPath(Path path) throws IOException;
}
