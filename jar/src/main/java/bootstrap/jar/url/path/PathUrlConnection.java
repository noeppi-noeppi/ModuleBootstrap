package bootstrap.jar.url.path;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

@NotNullByDefault
public class PathUrlConnection extends URLConnection {

    private final Path path;
    private @Nullable InputStream in;

    public PathUrlConnection(URL url, Path path) {
        super(url);
        this.path = path;
        this.in = null;
    }

    @Override
    public void connect() throws IOException {
        if (this.in == null) {
            try {
                this.in = Files.newInputStream(this.path);
                this.connected = true;
            } catch (NoSuchFileException e) {
                FileNotFoundException ex = new FileNotFoundException(this.url.toString());
                ex.initCause(e);
                throw ex;
            }
        }
    }

    @Override
    public long getContentLengthLong() {
        try {
            return Files.size(this.path);
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public @Nullable String getContentType() {
        try {
            return Files.probeContentType(this.path);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public long getDate() {
        try {
            return Files.getLastModifiedTime(this.path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(this.path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        this.connect();
        return Objects.requireNonNull(this.in);
    }
}
