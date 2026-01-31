package bootstrap.api;

import bootstrap.spi.Entrypoint;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.invoke.MethodHandles;

/**
 * This class provides access back into the loader. This allows for example to perform privileged operations.
 */
@NotNullByDefault
public interface ModuleSystem {

    /**
     * Retrieves the trusted lookup. The trusted lookup may access anything without any access restrictions including
     * for example final fields.
     */
    MethodHandles.Lookup trustedLookup();

    /**
     * Gets the boot layer that lies below the {@link #layer() bootstrap layer}. This is not necessarily
     * the same as {@link ModuleLayer#boot()} if running with legacy launcher.
     */
    ModuleLayer bootLayer();

    /**
     * Gets the bootstrap layer of the module system. This is the layer, in which the {@link Entrypoint entrypoint}
     * itself is loaded. It is a child of the {@link #bootLayer() boot layer}.
     */
    ModuleLayer layer();

    /**
     * Update module {@code source} in the {@link #layer() bootstrap layer} to open package {@code pkg} to
     * module {@code target}. This method follows the same semantics as
     * {@link ModuleLayer.Controller#addOpens(Module, String, Module)}.
     */
    void addOpens(Module source, String pkg, Module target);

    /**
     * Update module {@code source} in the {@link #layer() bootstrap layer} to export package {@code pkg} to
     * module {@code target}. This method follows the same semantics as
     * {@link ModuleLayer.Controller#addExports(Module, String, Module)}.
     */
    void addExports(Module source, String pkg, Module target);

    /**
     * Enables native access for module {@code target} in the {@link #layer() bootstrap layer}.
     */
    void enableNativeAccess(Module target);
}
