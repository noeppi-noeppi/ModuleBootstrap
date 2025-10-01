package bootstrap.jar.url.classtransformer;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;

@NotNullByDefault
public class ClassFileUrlConnection extends URLConnection {

    private final byte[] data;
    private final String mime;
    private @Nullable InputStream in;

    public ClassFileUrlConnection(URL url, byte[] data, String mime) {
        super(url);
        this.data = data;
        this.mime = mime;
    }

    @Override
    public void connect() {
        if (this.in == null) {
            this.in = new ByteArrayInputStream(this.data);
        }
    }

    @Override
    public int getContentLength() {
        return this.data.length;
    }

    @Override
    public long getContentLengthLong() {
        return this.data.length;
    }

    @Override
    public String getContentType() {
        return this.mime;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        this.connect();
        return Objects.requireNonNull(this.in);
    }
}
