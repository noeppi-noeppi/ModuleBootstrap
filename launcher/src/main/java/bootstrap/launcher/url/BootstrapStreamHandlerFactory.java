package bootstrap.launcher.url;

import bootstrap.api.LauncherConstants;
import bootstrap.spi.ProtocolProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.spi.URLStreamHandlerProvider;
import java.util.*;
import java.util.stream.Collectors;

@NotNullByDefault
public class BootstrapStreamHandlerFactory implements URLStreamHandlerFactory {

    private static final Set<String> PLATFORM_PROTOCOLS = Set.of("file", "jar", "jrt");
    private static final Set<String> BOOTSTRAP_PROTOCOLS = Set.of("empty", "union", "layered", "classtransformer");

    private final Object lock;

    private final List<URLStreamHandlerFactory> factories;
    private @Nullable Map<String, ProtocolStreamHandler> protocolProviders;

    public BootstrapStreamHandlerFactory(ModuleLayer bootLayer) {
        this.lock = new Object();

        Module bootstrapJar = bootLayer.findModule(LauncherConstants.MODULE_JAR).orElseThrow(() -> new RuntimeException("Module not found on the boot module path: " + LauncherConstants.MODULE_JAR));

        this.factories = ServiceLoader.load(bootLayer, URLStreamHandlerProvider.class).stream()
                .filter(provider -> provider.type().getModule() == bootstrapJar)
                .<URLStreamHandlerFactory>map(ServiceLoader.Provider::get)
                .toList();
        this.protocolProviders = null;
    }

    public void loadProtocolProviders(ModuleLayer bootstrapLayer) {
        synchronized (this.lock) {
            if (this.protocolProviders != null) throw new RuntimeException("The bootstrap layer has already been provided.");
            List<ProtocolProvider> protocolProviderList = ServiceLoader.load(bootstrapLayer, ProtocolProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
            Map<String, ProtocolProvider> protocolProviderMap = new HashMap<>();
            for (ProtocolProvider provider : protocolProviderList) {
                String protocol = provider.protocol();
                if (PLATFORM_PROTOCOLS.contains(protocol) || BOOTSTRAP_PROTOCOLS.contains(protocol)) {
                    throw new ServiceConfigurationError("Protocol providers for reserved protocol " + protocol + " detected: " + provider.getClass().getName());
                } else if (protocolProviderMap.containsKey(protocol)) {
                    throw new ServiceConfigurationError("Multiple protocol providers for protocol " + protocol + " detected: " + protocolProviderMap.get(protocol).getClass().getName() + " and " + provider.getClass().getName());
                } else {
                    protocolProviderMap.put(protocol, provider);
                }
            }
            @SuppressWarnings("Convert2MethodRef")
            Map<String, ProtocolStreamHandler> protocolProviders = protocolProviderMap.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                    entry -> entry.getKey(),
                    entry -> new ProtocolStreamHandler(entry.getValue())
            ));
            try {
                VarHandle vh = MethodHandles.lookup().findVarHandle(BootstrapStreamHandlerFactory.class, "protocolProviders", Map.class);
                vh.setVolatile(this, protocolProviders);
            } catch (Exception e) {
                this.protocolProviders = protocolProviders;
            }
        }
    }

    @Override
    public @Nullable URLStreamHandler createURLStreamHandler(String protocol) {
        if (PLATFORM_PROTOCOLS.contains(protocol)) return null;
        for (URLStreamHandlerFactory factory : this.factories) {
            URLStreamHandler handler = factory.createURLStreamHandler(protocol);
            if (handler != null) return handler;
        }
        if (BOOTSTRAP_PROTOCOLS.contains(protocol)) return null;
        return this.protocolProviders != null ? this.protocolProviders.get(protocol) : null;
    }
}
