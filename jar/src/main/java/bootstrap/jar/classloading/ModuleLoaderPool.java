package bootstrap.jar.classloading;

import bootstrap.jar.impl.classloading.LoaderPoolController;
import bootstrap.jar.impl.classloading.LoaderPoolImpl;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.Configuration;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@NotNullByDefault
public interface ModuleLoaderPool extends Function<String, ClassLoader> {

    /**
     * Retrieves the name of this loader pool.
     */
    String name();

    /**
     * Retrieves the {@link Configuration configuration} for this loader pool.
     */
    Configuration configuration();

    /**
     * Retrieve the {@link ClassLoader} for a module.
     *
     * @throws IllegalArgumentException if the module is not known to this loader pool.
     */
    @Override
    ClassLoader apply(String moduleName);

    /**
     * Returns a {@link ClassDiscovery} that allows retrieving class bytes from the view of the given module.
     * The returned {@link Optional} is empty if the module is not known to this loader pool.
     *
     * @throws IllegalArgumentException if the module is not known to this loader pool.
     */
    ClassDiscovery discovery(String moduleName);

    /**
     * Adds a runtime class to this pool. Runtime classes can only be added to packages that already exist
     * in a module. If a runtime class is added that already exists, the call is silently ignored. Runtime classes
     * are not subject to transformation by the {@link ClassTransformer}.
     *
     * @param moduleName The module to which the class should be added.
     * @param className The qualified name of the class to be added.
     * @param resource The resource from where the class should be loaded.
     * @throws IllegalArgumentException If the given module is not part of the layer or the package of the class is not part of the module.
     */
    void addRuntimeClass(String moduleName, String className, URL resource);

    /**
     * Creates a loader pool with a single loader for all modules and defines a module layer from that pool.
     */
    static ModuleLoaderPool.Controller defineWithOneLoader(String name, Configuration configuration, List<ModuleLayer> parentLayers, ClassTransformer transformer) {
        return define(name, configuration, parentLayers, transformer, moduleName -> "");
    }

    /**
     * Creates a loader pool with a separate loader for every module and defines a module layer from that pool.
     */
    static ModuleLoaderPool.Controller defineWithManyLoaders(String name, Configuration configuration, List<ModuleLayer> parentLayers, ClassTransformer transformer) {
        return define(name, configuration, parentLayers, transformer, Function.identity());
    }

    /**
     * Creates a loader pool and defines a module layer from that pool. Care has to be taken if the
     * {@link ModuleLayer.Controller#addReads(Module, Module)} method from the returned controller is used. It
     * may lead to an unexpected {@link NoClassDefFoundError} if used between modules loaded from different loaders.
     */
    static ModuleLoaderPool.Controller define(String name, Configuration configuration, List<ModuleLayer> parentLayers, ClassTransformer transformer, Function<String, String> cluster) {
        ModuleLoaderPool pool = create(name, configuration, parentLayers, transformer, cluster);
        return new LoaderPoolController((LoaderPoolImpl) pool, ModuleLayer.defineModules(configuration, parentLayers, pool));
    }

    /**
     * Creates a loader pool using the provided settings.
     *
     * @param name The name of the loader pool.
     * @param configuration The {@link Configuration} of the {@link ModuleLayer} that is to be built by the provided pool.
     * @param parentLayers The parent layers that will be used to define the {@link ModuleLayer}
     * @param transformer A {@link ClassTransformer} to transform classes loaded by this pool.
     * @param cluster A function to cluster the modules from the provided {@link Configuration}. The clustering function
     *                assigns a cluster identifier to each module. Modules with the same cluster identifier will be loaded
     *                from the same class loader.
     */
    static ModuleLoaderPool create(String name, Configuration configuration, List<ModuleLayer> parentLayers, ClassTransformer transformer, Function<String, String> cluster) {
        return new LoaderPoolImpl(name, configuration, parentLayers, transformer, cluster, ClassLoader.getPlatformClassLoader());
    }

    /**
     * A controller for a {@link ModuleLayer} defined with a {@link ModuleLoaderPool}.
     */
    @NotNullByDefault
    interface Controller {

        /**
         * The loader pool that was used to define the layer.
         */
        ModuleLoaderPool pool();

        /**
         * The controller of the defined layer. Care has to be taken when using this controllers
         * {@link java.lang.ModuleLayer.Controller#addReads(Module, Module) addReads} method as it won't update the
         * classloader. Use {@link #addReads(Module, Module)} instead.
         */
        ModuleLayer.Controller layerController();
        
        /**
         * The defined layer.
         */
        default ModuleLayer layer() {
            return this.layerController().layer();
        }

        /**
         * Invokes {@link ModuleLayer.Controller#addReads(Module, Module) addReads} on the
         * {@link #layerController() layer controller} and also updates the classloaders package mapping respectively.
         */
        void addReads(Module source, Module target);

        /**
         * Equivalent to {@link ModuleLayer.Controller#addOpens(Module, String, Module) addOpens} on
         * the {@link #layerController() layer controller}.
         */
        default void addOpens(Module source, String pkg, Module target) {
            this.layerController().addOpens(source, pkg, target);
        }

        /**
         * Equivalent to {@link ModuleLayer.Controller#addExports(Module, String, Module) addExports} on
         * the {@link #layerController() layer controller}.
         */
        default void addExports(Module source, String pkg, Module target) {
            this.layerController().addExports(source, pkg, target);
        }
    }
}
