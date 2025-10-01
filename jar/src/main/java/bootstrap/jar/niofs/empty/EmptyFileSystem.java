package bootstrap.jar.niofs.empty;

import bootstrap.jar.niofs.path.DefaultFileSystem;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
public class EmptyFileSystem extends DefaultFileSystem {

    public EmptyFileSystem(EmptyFileSystemProvider provider) {
        super(provider);
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }
}
