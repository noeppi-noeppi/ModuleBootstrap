package bootstrap.jar.classloading;

import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ResolvedModule;
import java.util.Optional;

/**
 * Allows access back into the classloader in a {@link ClassTransformer}. This class keeps a reference
 * to the class that is currently being transformed and only allows access to classes readable by that
 * class. The {@link #into(String)} method provides an escape-hatch for the {@link ClassTransformer} to
 * access content of modules not read by the current module.
 */
@NotNullByDefault
public interface TransformingEnvironment extends ClassDiscovery {

    /**
     * Gets a {@link TransformingEnvironment} that behaves as if a class was transformed that lies in
     * another module in the same layer. The returned Optional is empty if the module is not found or
     * lies in a parent layer.
     */
    Optional<TransformingEnvironment> into(String moduleName);

    /**
     * Gets the module, the currently transforming class is part of.
     */
    ResolvedModule module();

    /**
     * Gets the {@link ClassLoader} that is responsible for loading the currently transforming class.
     */
    ClassLoader loader();

    /**
     * Retrieves the module that provides the package of the given class. This does not check whether the
     * class exists. Returns an empty {@link Optional} if no module provides that package. This method does not respect
     * {@link bootstrap.jar.classloading.ModuleLoaderPool.Controller#addReads(Module, Module) module reads added at runtime}.
     */
    default Optional<ResolvedModule> moduleForClass(String className) {
        int idx = className.lastIndexOf('.');
        return idx < 0 ? Optional.empty() : this.moduleForPackage(className.substring(0, idx));
    }

    /**
     * Retrieves the module that provides the provided package. Returns an empty {@link Optional} if no module
     * provides that package. This method does not respect
     * {@link bootstrap.jar.classloading.ModuleLoaderPool.Controller#addReads(Module, Module) module reads added at runtime}.
     */
    Optional<ResolvedModule> moduleForPackage(String packageName);
}
