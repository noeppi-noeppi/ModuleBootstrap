package bootstrap.jar;

import bootstrap.jar.impl.filter.*;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Map;
import java.util.Set;

/**
 * Provides some default {@link JarMetadataFilter jar metadata filters}.
 */
@NotNullByDefault
public class JarMetadataFilters {

    private JarMetadataFilters() {}

    /**
     * Gets a filter that declares every module descriptor as open.
     */
    public static JarMetadataFilter openModule() {
        return OpenModuleFilter.INSTANCE;
    }

    /**
     * Creates a filter that ensures, that the module declares at least the provided packages.
     */
    public static JarMetadataFilter additionalPackages(Set<String> packages) {
        return new AdditionalPackagesFilter(packages);
    }

    /**
     * Force the provided module name for the jar.
     */
    public static JarMetadataFilter forcedModuleName(String moduleName) {
        return new ModuleNameFilter(moduleName, true);
    }

    /**
     * Gets a filter that lets the automatic module name default to the provided name.
     */
    public static JarMetadataFilter defaultModuleName(String automaticModuleName) {
        return new ModuleNameFilter(automaticModuleName, false);
    }

    /**
     * Gets a filter that tries to infer the module name from the file name if no module name is provided.
     */
    public static JarMetadataFilter fileInferredModuleName() {
        return FileInferredModuleNameFilter.INSTANCE;
    }

    /**
     * Gets a filter that remaps all module names encountered using the provided mapping. Module names that
     * have no entry in the provided map are left untouched.
     */
    public static JarMetadataFilter remapModuleNames(Map<String, String> remap) {
        return new ModuleRemapFilter(remap);
    }
}
