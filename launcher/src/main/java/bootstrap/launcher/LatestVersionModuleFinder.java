package bootstrap.launcher;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.*;
import java.util.stream.Collectors;

@NotNullByDefault
public class LatestVersionModuleFinder implements ModuleFinder {

    @SuppressWarnings("OptionalIsPresent")
    private static final Comparator<ModuleReference> BY_VERSION = (ref1, ref2) -> {
        Optional<ModuleDescriptor.Version> ver1 = ref1.descriptor().version();
        Optional<ModuleDescriptor.Version> ver2 = ref2.descriptor().version();
        if (ver1.isEmpty() && ver2.isEmpty()) return 0;
        if (ver1.isEmpty()) return -1;
        if (ver2.isEmpty()) return 1;
        return ver1.get().compareTo(ver2.get());
    };

    private final Object lock;
    private final List<ModuleFinder> finders;
    private final Map<String, Optional<ModuleReference>> modules;
    private @Nullable Set<ModuleReference> allModules;

    public LatestVersionModuleFinder(List<ModuleFinder> finders) {
        this.lock = new Object();
        this.finders = List.copyOf(finders);
        this.modules = new HashMap<>();
        this.allModules = null;
    }

    @Override
    public Optional<ModuleReference> find(String name) {
        synchronized (this.lock) {
            if (this.modules.containsKey(name)) {
                return this.modules.get(name);
            }
            List<ModuleReference> refs = new ArrayList<>();
            for (ModuleFinder finder : this.finders) {
                finder.find(name).ifPresent(refs::add);
            }
            Optional<ModuleReference> latest = refs.stream().max(BY_VERSION);
            this.modules.put(name, latest);
            return latest;
        }
    }

    @Override
    public Set<ModuleReference> findAll() {
        synchronized (this.lock) {
            if (this.allModules != null) return this.allModules;
            this.allModules = this.finders.stream()
                    .flatMap(finder -> finder.findAll().stream())
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .map(this::find)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
            return this.allModules;
        }
    }
}
