package bootstrap.jar.url.path;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

@NotNullByDefault
public class PathStreamHandlerProvider extends URLStreamHandlerProvider {

    public PathStreamHandlerProvider() {}

    @Override
    public @Nullable URLStreamHandler createURLStreamHandler(String protocol) {
        return switch (protocol) {
            case "empty", "union", "layered" -> PathStreamHandler.INSTANCE;
            default -> null;
        };
    }
}
