package bootstrap.spi;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Can be provided as a {@link java.util.ServiceLoader service} in the bootstrap layer to provide additional URL
 * protocols.
 */
@NotNullByDefault
public interface ProtocolProvider {

    /**
     * The name of the protocol.
     */
    String protocol();

    /**
     * Opens a connection to a URL of the protocol, this provider is responsible for.
     */
    URLConnection connection(URL url) throws IOException;
}
