package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.spi.FileSystemProvider;

@NotNullByDefault
public abstract class BaseFileSystemProvider extends FileSystemProvider {

    public abstract boolean isReadOnly();
    public abstract void unregisterFileSystem(FileSystem fs) throws IOException;
    public abstract URI toURI(BasePath path) throws URISyntaxException;
}
