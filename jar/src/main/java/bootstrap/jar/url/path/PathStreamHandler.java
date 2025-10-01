package bootstrap.jar.url.path;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.*;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;

@NotNullByDefault
public class PathStreamHandler extends URLStreamHandler {

    public static final PathStreamHandler INSTANCE = new PathStreamHandler();

    private PathStreamHandler() {}

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        try {
            URI uri = url.toURI();
            return new PathUrlConnection(url, Path.of(uri));
        } catch (URISyntaxException | FileSystemNotFoundException e) {
            throw new IOException("Failed to open connection to " + url, e);
        }
    }
}
