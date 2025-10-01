package bootstrap.launcher.plugin;

import bootstrap.api.LauncherConstants;
import bootstrap.jar.Jar;
import bootstrap.jar.JarMetadataFilters;
import bootstrap.jar.JarModuleFinder;
import bootstrap.spi.BootPlugin;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NotNullByDefault
public class DefaultBootPlugin implements BootPlugin {

    private @Nullable JarModuleFinder modulePath;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public void initialize(ModuleLayer bootLayer) throws IOException {
        if (Boolean.parseBoolean(System.getProperty(LauncherConstants.PROP_DEFAULT_BOOT, "true"))) {
            List<String> cp = List.of(Objects.requireNonNullElse(System.getProperty(LauncherConstants.PROP_CLASSPATH), "").split(Pattern.quote(File.pathSeparator), -1));
            List<Jar> jars = new ArrayList<>(cp.size());
            for (String cpEntry : cp) {
                if (cpEntry.isEmpty()) continue;
                Path path = Path.of(cpEntry);
                if (Files.exists(path)) {
                    jars.add(Jar.of(JarMetadataFilters.fileInferredModuleName(), path));
                }
            }
            this.modulePath = JarModuleFinder.of(jars);
        }
    }

    @Override
    public Set<String> rootModules() {
        if (this.modulePath == null) return Set.of();
        return this.modulePath.findAll().stream()
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ModuleFinder findModules() {
        return this.modulePath == null ? ModuleFinder.of() : this.modulePath;
    }
}
