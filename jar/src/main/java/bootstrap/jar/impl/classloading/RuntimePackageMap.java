package bootstrap.jar.impl.classloading;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@NotNullByDefault
public class RuntimePackageMap {

    private final ClassLoader loader;
    private final LoaderPoolImpl pool;
    private final Map<String, String> staticPackageMap;
    private final Map<String, Module> map;
    private final ReadWriteLock lock;

    public RuntimePackageMap(ClassLoader loader, LoaderPoolImpl pool, Map<String, String> staticPackageMap) {
        this.loader = loader;
        this.pool = pool;
        this.staticPackageMap = staticPackageMap;
        this.map = new HashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void addModuleReads(ModuleLayer.Controller layerController, Module source, Module target) {
        if (!source.isNamed() || !target.isNamed()) {
            throw new IllegalArgumentException("addModuleReads cannot be used on unnamed modules.");
        }
        if (source.getClassLoader() != this.loader) {
            throw new IllegalArgumentException("The module " + source.getName() + " is not loaded by this classloader.");
        }

        this.lock.writeLock().lock();
        try {
            // Check that we can add the module to the package map without conflict before adding the read on the
            // layer controller, so the operation fails or succeeds as a whole.
            Map<String, Module> newMappings = new HashMap<>();
            if (target.getClassLoader() != this.loader) {
                for (String pkg : target.getPackages()) {
                    if (this.pool.getClassLoaderOrNull(this.staticPackageMap.get(pkg)) == target.getClassLoader()) {
                        // The package is already statically bound to the loader, nothing to do.
                        continue;
                    }
                    if (this.staticPackageMap.containsKey(pkg) || (this.map.containsKey(pkg) && this.map.get(pkg) != target)) {
                        throw new IllegalStateException("Can't make module " + source.getName() + " read module " + target.getName() + " as it would produce split packages: " + pkg);
                    }
                    newMappings.put(pkg, target);
                }
            }
            // Attempt addReads
            layerController.addReads(source, target);
            this.map.putAll(newMappings);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public Optional<Module> getRuntimePackage(String pkg) {
        this.lock.readLock().lock();
        try {
            return Optional.ofNullable(this.map.get(pkg));
        } finally {
            this.lock.readLock().unlock();
        }
    }
}
