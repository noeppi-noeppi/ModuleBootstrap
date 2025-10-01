import bootstrap.launcher.plugin.DefaultBootPlugin;
import bootstrap.spi.BootPlugin;
import bootstrap.spi.Entrypoint;
import bootstrap.spi.ProtocolProvider;

import java.net.spi.URLStreamHandlerProvider;

module bootstrap.launcher {
    requires java.base;
    requires bootstrap.spi;
    requires bootstrap.jar;
    requires static org.jetbrains.annotations;

    // Required, so ClassLoader can access LegacyLauncher
    exports bootstrap.launcher to java.base;

    uses BootPlugin;
    uses ProtocolProvider;
    uses Entrypoint;
    uses URLStreamHandlerProvider;

    provides BootPlugin with DefaultBootPlugin;
}
