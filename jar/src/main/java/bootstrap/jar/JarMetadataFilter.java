package bootstrap.jar;

import bootstrap.jar.impl.filter.CompositeMetadataFilter;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;

/**
 * A filter for jar metadata when building {@link Jar} files.
 */
@NotNullByDefault
public interface JarMetadataFilter {

    /**
     * Invoked when a jar has no explicit module descriptor. This method receives the detected automatic module name.
     * By default, the jar file name is not taken into account and a jar that does not have an {@code Automatic-Module-Name}
     * attribute will have no detected module name.
     * <p>
     * If a jar file has no automatic module name after the filters ran, this is an error condition and will fail to
     * produce a jar.
     *
     * @param automaticModuleName The detected automatic module name if any.
     * @param paths The raw paths used to create the jar file.
     * @param fs The jar filesystem.
     * @return A new automatic module.
     */
    default Optional<String> filterAutomaticModuleName(Optional<String> automaticModuleName, List<Path> paths, FileSystem fs) {
        return automaticModuleName;
    }

    /**
     * Invoked with the inferred manifest for the {@link Jar} that is about to be built. The inferred manifest
     * will contain attributes from the final module descriptor as described in {@link Jar}.
     *
     * @param manifest The manifest inferred from the jar file. This manifest may be modified.
     * @param fs The jar filesystem.
     * @param descriptor The final module descriptor after filtering.
     * @return A manifest for the {@link Jar}. This may be the same as the passed manifest.
     */
    default Manifest filterManifest(Manifest manifest, FileSystem fs, ModuleDescriptor descriptor) {
        return manifest;
    }

    /**
     * Invoked with the inferred module descriptor for the {@link Jar} that is about to be built. This
     * is either the module descriptor shipped in the jar itself or an automatic module descriptor inferred
     * from the jar contents.
     *
     * @param descriptor The module descriptor inferred from the jar file.
     * @param fs The jar filesystem.
     * @return A module descriptor for the {@link Jar}. This may be the same as the passed module descriptor.
     */
    default ModuleDescriptor filterModuleDescriptor(ModuleDescriptor descriptor, FileSystem fs) {
        return descriptor;
    }

    /**
     * Creates a {@link JarMetadataFilter} that applies the given filter in that order.
     */
    static JarMetadataFilter of(JarMetadataFilter... filters) {
        return of(Arrays.asList(filters));
    }

    /**
     * Creates a {@link JarMetadataFilter} that applies the given filter in that order.
     */
    static JarMetadataFilter of(List<JarMetadataFilter> filters) {
        return CompositeMetadataFilter.create(filters);
    }
}
