package bootstrap.spi;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.util.Set;

/**
 * Can be provided as a {@link java.util.ServiceLoader service} in the boot layer to alter the bootstrap layer.
 * Note that when launching through legacy launcher, modules that provide a {@link BootPlugin boot plugin}
 * will be placed on the boot layer automatically. <b>However, their dependencies are not.</b> It is recommended
 * that boot plugins don't depend on any additional modules.
 */
@NotNullByDefault
public interface BootPlugin {

    /**
     * Gets the name of this boot plugin.
     */
    String name();

    /**
     * Invoked with the boot layer very early in the loading process.
     *
     * @param bootLayer The boot layer. This is not necessarily equal to {@link ModuleLayer#boot()} if running with legacy launcher.
     */
    void initialize(ModuleLayer bootLayer) throws IOException;

    /**
     * Retrieves the list of modules, this boot plugin wishes to load on the bootstrap classpath. These will be resolved
     * in the bootstrap configuration.
     */
    Set<String> rootModules();

    /**
     * Retrieves a {@link ModuleFinder module finder} for all modules known to this boot plugin. If multiple boot plugins
     * provide the same modules, the latest version is chosen.
     */
    ModuleFinder findModules();

    /**
     * Invoked after the bootstrap layer has been built but before the {@link Entrypoint entrypoint} starts.
     */
    default void consumeLayer(ModuleLayer bootstrapLayer) {}
}
