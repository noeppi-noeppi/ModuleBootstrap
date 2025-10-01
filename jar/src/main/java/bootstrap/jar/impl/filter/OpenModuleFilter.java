package bootstrap.jar.impl.filter;

import bootstrap.jar.JarMetadataFilter;
import bootstrap.jar.util.ModuleHelper;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NotNullByDefault
public class OpenModuleFilter implements JarMetadataFilter {

    public static final OpenModuleFilter INSTANCE = new OpenModuleFilter();

    private OpenModuleFilter() {}

    @Override
    public ModuleDescriptor filterModuleDescriptor(ModuleDescriptor descriptor, FileSystem fs) {
        if (descriptor.isOpen() || descriptor.isAutomatic()) return descriptor;
        Set<ModuleDescriptor.Modifier> newModifiers = Stream.concat(descriptor.modifiers().stream(), Stream.of(ModuleDescriptor.Modifier.OPEN))
                .collect(Collectors.toUnmodifiableSet());
        return ModuleHelper.builder(descriptor.name(), newModifiers, descriptor).build();
    }
}
