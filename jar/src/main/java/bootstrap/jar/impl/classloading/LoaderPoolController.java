package bootstrap.jar.impl.classloading;

import bootstrap.jar.classloading.ModuleLoaderPool;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@NotNullByDefault
public record LoaderPoolController(LoaderPoolImpl pool, ModuleLayer.Controller layerController) implements ModuleLoaderPool.Controller {

    @Override
    public void addReads(Module source, Module target) {
        if (!source.isNamed()) throw new IllegalArgumentException("addReads cannot be used on the unnamed module.");
        @Nullable ModularClassLoader sourceLoader = this.pool.getClassLoaderOrNull(source.getName());
        if (sourceLoader == null || sourceLoader != source.getClassLoader()) {
            throw new IllegalArgumentException("Module " + source.getName() + " is not part of this loader pool.");
        }

        Optional<Module> moduleFromLayer = this.layer().findModule(source.getName());
        if (moduleFromLayer.isEmpty() || moduleFromLayer.get() != source) {
            throw new IllegalArgumentException("Module " + source.getName() + " is not part of this module layer.");
        }
        
        sourceLoader.addModuleReads(this.layerController(), source, target);
    }
}
