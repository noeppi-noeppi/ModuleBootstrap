package bootstrap.jar.impl.filter;

import bootstrap.jar.JarMetadataFilter;
import bootstrap.jar.util.ModuleHelper;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@NotNullByDefault
@SuppressWarnings("ClassCanBeRecord")
public class ModuleRemapFilter implements JarMetadataFilter {

    private static final int COPY_OPTIONS = ModuleHelper.COPY_ALL & ~(ModuleHelper.COPY_REQUIRES | ModuleHelper.COPY_EXPORTS | ModuleHelper.COPY_OPENS);

    private final Map<String, String> remap;

    public ModuleRemapFilter(Map<String, String> remap) {
        this.remap = Map.copyOf(remap);
    }

    private String remapModuleName(String moduleName) {
        return this.remap.getOrDefault(moduleName, moduleName);
    }

    @Override
    public Optional<String> filterAutomaticModuleName(Optional<String> automaticModuleName, List<Path> paths, FileSystem fs) {
        return automaticModuleName.map(this::remapModuleName);
    }

    @Override
    public ModuleDescriptor filterModuleDescriptor(ModuleDescriptor descriptor, FileSystem fs) {
        ModuleDescriptor.Builder builder = ModuleHelper.builder(this.remapModuleName(descriptor.name()), descriptor.modifiers(), descriptor, COPY_OPTIONS);
        if (!descriptor.isAutomatic()) {
            for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                if (requires.compiledVersion().isPresent()) {
                    builder.requires(requires.modifiers(), this.remapModuleName(requires.name()), requires.compiledVersion().get());
                } else {
                    builder.requires(requires.modifiers(), this.remapModuleName(requires.name()));
                }
            }
            for (ModuleDescriptor.Exports exports : descriptor.exports()) {
                if (exports.isQualified()) {
                    builder.exports(exports.modifiers(), exports.source(), exports.targets().stream().map(this::remapModuleName).collect(Collectors.toUnmodifiableSet()));
                } else {
                    builder.exports(exports);
                }
            }
        }
        if (!descriptor.isAutomatic() && !descriptor.isOpen()) {
            for (ModuleDescriptor.Opens opens : descriptor.opens()) {
                if (opens.isQualified()) {
                    builder.opens(opens.modifiers(), opens.source(), opens.targets().stream().map(this::remapModuleName).collect(Collectors.toUnmodifiableSet()));
                } else {
                    builder.opens(opens);
                }
            }
        }
        return builder.build();
    }
}
