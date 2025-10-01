package bootstrap.jar.impl.filter;

import bootstrap.jar.JarMetadataFilter;
import bootstrap.jar.util.ModuleHelper;
import bootstrap.jar.util.NameHelper;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@NotNullByDefault
@SuppressWarnings("ClassCanBeRecord")
public class ModuleNameFilter implements JarMetadataFilter {

    private final String moduleName;
    private final boolean force;

    public ModuleNameFilter(String moduleName, boolean force) {
        if (!NameHelper.validTypeName(moduleName)) {
            throw new IllegalArgumentException("Invalid module name: '" + moduleName + "'");
        }
        this.moduleName = moduleName;
        this.force = force;
    }

    @Override
    public Optional<String> filterAutomaticModuleName(Optional<String> automaticModuleName, List<Path> paths, FileSystem fs) {
        if (this.force) {
            return Optional.of(this.moduleName);
        } else {
            return automaticModuleName.or(() -> Optional.of(this.moduleName));
        }
    }

    @Override
    public ModuleDescriptor filterModuleDescriptor(ModuleDescriptor descriptor, FileSystem fs) {
        if (this.force) {
            if (Objects.equals(this.moduleName, descriptor.name())) return descriptor;
            return ModuleHelper.builder(this.moduleName, descriptor.modifiers(), descriptor).build();
        } else {
            return descriptor;
        }
    }
}
