package bootstrap.api;

import bootstrap.spi.Entrypoint;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * Provides certain ModuleBootstrap constants. 
 */
@NotNullByDefault
public class LauncherConstants {

    /**
     * The module name of the launcher module.
     */
    public static final String MODULE_LAUNCHER = "bootstrap.launcher";

    /**
     * The module name of the spi module.
     */
    public static final String MODULE_SPI = "bootstrap.spi";

    /**
     * The module name of the jar module.
     */
    public static final String MODULE_JAR = "bootstrap.jar";

    /**
     * Common property name with a folder name that serves as application home. This is optional and not used by
     * ModuleBootstrap itself.
     */
    public static final String PROP_HOME = "bootstrap.home";

    /**
     * System property read by the default boot plugin and assembled into a module path for the bootstrap layer.
     * Every entry in here will be resolved. Follows the same syntax as the {@code java.class.path} property.
     * <p>
     * If using legacy launcher, this property will be automatically set to contain every entry from the classpath that
     * was not placed in the legacy boot layer.
     */
    public static final String PROP_CLASSPATH = "bootstrap.classpath";

    /**
     * System property that is parsed as a boolean value, defaults to {@code true}. If set to {@code false}, the
     * default boot plugin will not run.
     */
    public static final String PROP_DEFAULT_BOOT = "bootstrap.defaultboot";

    /**
     * System property to set the name of the {@link Entrypoint} to launch.
     */
    public static final String PROP_ENTRYPOINT = "bootstrap.entrypoint";
}
