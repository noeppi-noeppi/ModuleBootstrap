package bootstrap.jar.impl.filter;

import bootstrap.jar.JarMetadataFilter;
import bootstrap.jar.util.ModuleHelper;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.util.Set;

@NotNullByDefault
@SuppressWarnings("ClassCanBeRecord")
public class AdditionalPackagesFilter implements JarMetadataFilter {

    private final Set<String> packages;

    public AdditionalPackagesFilter(Set<String> packages) {
        this.packages = Set.copyOf(packages);
    }

    @Override
    public ModuleDescriptor filterModuleDescriptor(ModuleDescriptor descriptor, FileSystem fs) {
        if (descriptor.packages().containsAll(this.packages)) return descriptor;
        return ModuleHelper.builder(descriptor).packages(this.packages).build();
    }
}
