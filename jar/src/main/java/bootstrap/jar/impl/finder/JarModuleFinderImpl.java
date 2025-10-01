package bootstrap.jar.impl.finder;

import bootstrap.jar.Jar;
import bootstrap.jar.JarModuleFinder;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.util.*;
import java.util.stream.Collectors;

@NotNullByDefault
public class JarModuleFinderImpl implements JarModuleFinder {

    private final Map<String, Jar> jarMap;
    private final Map<String, ModuleReference> modules;
    private final Set<ModuleReference> moduleSet;

    public JarModuleFinderImpl(List<Jar> jars) {
        Map<String, Jar> jarMap = new HashMap<>();
        for (Jar jar : jars) {
            String moduleName = jar.descriptor().name();
            if (jarMap.containsKey(moduleName)) {
                throw new ResolutionException("Duplicate module " + moduleName);
            }
            jarMap.put(moduleName, jar);
        }
        this.jarMap = Map.copyOf(jarMap);
        this.modules = this.jarMap.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().reference()));
        this.moduleSet = Set.copyOf(this.modules.values());
    }

    @Override
    public Optional<ModuleReference> find(String name) {
        return Optional.ofNullable(this.modules.get(name));
    }

    @Override
    public Optional<Jar> findJar(String name) {
        return Optional.ofNullable(this.jarMap.get(name));
    }

    @Override
    public Set<ModuleReference> findAll() {
        return this.moduleSet;
    }
}
