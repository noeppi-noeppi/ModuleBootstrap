package bootstrap.launcher.url;

import bootstrap.spi.ProtocolProvider;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

@NotNullByDefault
public class ProtocolStreamHandler extends URLStreamHandler {

    private final ProtocolProvider provider;

    public ProtocolStreamHandler(ProtocolProvider provider) {
        this.provider = provider;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return this.provider.connection(url);
    }
}
