package bootstrap.jar.impl.filter;

import bootstrap.jar.JarMetadataFilter;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Stream;

@NotNullByDefault
@SuppressWarnings("ClassCanBeRecord")
public class CompositeMetadataFilter implements JarMetadataFilter {

    private final List<JarMetadataFilter> filters;

    private CompositeMetadataFilter(List<JarMetadataFilter> filters) {
        this.filters = List.copyOf(filters);
    }

    public static JarMetadataFilter create(List<JarMetadataFilter> filters) {
        List<JarMetadataFilter> flattened = filters.stream()
                .flatMap(f -> f instanceof CompositeMetadataFilter c ? c.filters.stream() : Stream.of(f))
                .toList();
        return flattened.size() == 1 ? flattened.getFirst() : new CompositeMetadataFilter(flattened);
    }

    @Override
    public Optional<String> filterAutomaticModuleName(Optional<String> automaticModuleName, List<Path> paths, FileSystem fs) {
        for (JarMetadataFilter filter : this.filters) {
            automaticModuleName = filter.filterAutomaticModuleName(automaticModuleName, paths, fs);
        }
        return automaticModuleName;
    }

    @Override
    public Manifest filterManifest(Manifest manifest, FileSystem fs, ModuleDescriptor descriptor) {
        for (JarMetadataFilter filter : this.filters) {
            manifest = filter.filterManifest(manifest, fs, descriptor);
        }
        return manifest;
    }

    @Override
    public ModuleDescriptor filterModuleDescriptor(ModuleDescriptor descriptor, FileSystem fs) {
        for (JarMetadataFilter filter : this.filters) {
            descriptor = filter.filterModuleDescriptor(descriptor, fs);
        }
        return descriptor;
    }
}
