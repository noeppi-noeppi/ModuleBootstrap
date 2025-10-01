package bootstrap.jar.niofs.layered;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;

@NotNullByDefault
public class ZipFileSystemStore {

    private static final Object LOCK = new Object();
    private static final Map<URI, FileSystem> fileSystems = new HashMap<>();

    public static FileSystem openZipFileSystem(Path zipPath) throws IOException {
        URI uri = zipPath.toUri();
        synchronized (LOCK) {
            FileSystem existing = fileSystems.get(uri);
            if (existing != null) return existing;
        }

        FileSystem newFS = null;
        for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            if ("jar".equals(provider.getScheme())) {
                newFS = provider.newFileSystem(zipPath, Map.of());
                break;
            }
        }

        if (newFS == null) throw new IOException("Failed to open layered zip filesystem: " + zipPath.toUri());
        synchronized (LOCK) {
            FileSystem existing = fileSystems.get(uri);
            if (existing != null) return existing;
            fileSystems.put(uri, newFS);
            return newFS;
        }
    }
}
