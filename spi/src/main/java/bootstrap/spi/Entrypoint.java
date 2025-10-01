package bootstrap.spi;

import bootstrap.api.ModuleSystem;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * Can be provided as a {@link java.util.ServiceLoader service} in the bootstrap layer. If there is only a single
 * entrypoint, it will be launched by the loader. Otherwise, the entrypoint can be set using the {@code PROP_ENTRYPOINT}
 * property.
 */
@NotNullByDefault
public interface Entrypoint {

    /**
     * Gets the name of this entrypoint.
     */
    String name();

    /**
     * Runs this entrypoint.
     */
    void main(ModuleSystem system, String[] args) throws Throwable;
}
