package bootstrap.jar.url.classtransformer;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

@NotNullByDefault
public class ClassTransformerStreamHandlerProvider extends URLStreamHandlerProvider {

    public ClassTransformerStreamHandlerProvider() {}

    @Override
    public @Nullable URLStreamHandler createURLStreamHandler(String protocol) {
        return switch (protocol) {
            case ClassTransformerStreamHandler.PROTOCOL -> ClassTransformerStreamHandler.INSTANCE;
            default -> null;
        };
    }
}
