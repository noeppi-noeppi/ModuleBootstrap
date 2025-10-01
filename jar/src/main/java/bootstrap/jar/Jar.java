package bootstrap.jar;

import bootstrap.jar.impl.JarFactory;
import bootstrap.jar.impl.JarPatcher;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;

/**
 * A modular jar file with metadata.
 * <p>
 * Jar files are created from a non-empty list of {@link Path paths} an optionally a {@link JarMetadataFilter}.
 * The jar creation process is subject to the following rules:
 * <ul>
 *     <li>All provided paths are scanned. If no paths were provided or at least one path does not exist, fail.</li>
 *     <li>For each path that is not a directory, assume, it's an archive and replace it with a layered file system of that path.</li>
 *     <li>If the list of paths contains more than one path, construct a union filesystem of all paths. Otherwise, the single path becomes the jar root.</li>
 *     <li>Read the manifest from the constructed jar root. If no manifest file is found, create a dummy manifest on the fly.</li>
 *     <li>If the jar file is a multi-release jar according to its manifest, replace the jar root with a union filesystem incorporating the applicable multi-release overrides.</li>
 *     <li>Try to find a module descriptor named {@code module-info.class} in the jar root and read it. If none is found, create an automatic module descriptor from the jar metadata such as manifest and service files.</li>
 *     <li>If the module descriptor found in the previous step has no main class or version set but the manifest includes a {@code Main-Class} or {@code Implementation-Version} main attribute, copy these values into the module descriptor.</li>
 *     <li>Run the jar metadata filter on the module descriptor.</li>
 *     <li>Populate the {@code Automatic-Module-Name}, {@code Main-Class} and {@code Implementation-Version} main attributes in the manifest from the module descriptor.</li>
 *     <li>Run the jar metadata filter on the manifest.</li>
 * </ul>
 * <p>
 * By default, the file name is not taken into account when no automatic module name has been specified. Use {@link JarMetadataFilters#fileInferredModuleName()} to get this behaviour.
 */
@NotNullByDefault
public interface Jar {

    /**
     * Return a copy of the manifest for this jar file.
     */
    Manifest manifest();

    /**
     * Returns the module descriptor for this jar file.
     */
    ModuleDescriptor descriptor();

    /**
     * Returns a module reference for this jar file.
     */
    ModuleReference reference();

    /**
     * Returns the {@link URI} root of this jar file.
     */
    URI uri();

    /**
     * Retrieves a path inside this jar file.
     */
    Path getPath(String first, String... more);

    /**
     * Gets the module name of this jar file.
     */
    default String name() {
        return this.descriptor().name();
    }

    /**
     * Gets the module version of this jar file.
     */
    default String version() {
        return this.descriptor().version().map(ModuleDescriptor.Version::toString).orElse("<unknown>");
    }

    /**
     * Gets a set of packages contained in the jar file.
     */
    default Set<String> packages() {
        return this.descriptor().packages();
    }

    /**
     * Creates a jar from the provided paths.
     */
    static Jar of(Path... paths) throws IOException {
        return of(JarMetadataFilter.of(), Arrays.asList(paths));
    }

    /**
     * Creates a jar from the provided paths.
     */
    static Jar of(List<Path> paths) throws IOException {
        return of(JarMetadataFilter.of(), paths);
    }

    /**
     * Creates a jar from the provided paths using the provided filter.
     */
    static Jar of(JarMetadataFilter filter, Path... paths) throws IOException {
        return of(filter, Arrays.asList(paths));
    }

    /**
     * Creates a jar from the provided paths using the provided filter.
     */
    static Jar of(JarMetadataFilter filter, List<Path> paths) throws IOException {
        return JarFactory.create(filter, paths);
    }

    /**
     * Creates a jar with no resources that declares the provided packages. Useful if one wishes to generate classes
     * into these packages at runtime.
     */
    static Jar empty(String moduleName, Set<String> packages) throws IOException {
        return empty(JarMetadataFilter.of(), moduleName, packages);
    }

    /**
     * Creates a jar with no resources that declares the provided packages. Useful if one wishes to generate classes
     * into these packages at runtime.
     */
    static Jar empty(JarMetadataFilter filter, String moduleName, Set<String> packages) throws IOException {
        return of(JarMetadataFilter.of(JarMetadataFilters.forcedModuleName(moduleName), JarMetadataFilters.additionalPackages(packages), filter), Path.of(URI.create("empty:/")));
    }

    /**
     * Takes an existing jar file and runs the provided metadata filter on it. Returns a new jar file with the
     * results from the filter.
     */
    static Jar patch(Jar jar, JarMetadataFilter filter) throws IOException {
        return JarPatcher.patch(jar, filter);
    }
}
