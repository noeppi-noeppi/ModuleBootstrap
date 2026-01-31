package bootstrap.launcher;

import bootstrap.api.LauncherConstants;
import bootstrap.api.ModuleSystem;
import bootstrap.jar.classloading.ClassTransformer;
import bootstrap.jar.classloading.ModuleLoaderPool;
import bootstrap.jar.reflect.JavaBaseAccess;
import bootstrap.launcher.url.BootstrapStreamHandlerFactory;
import bootstrap.spi.BootPlugin;
import bootstrap.spi.Entrypoint;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.util.*;

@NotNullByDefault
public class Main {

    public static void main(String[] args) throws Throwable {
        ModuleLayer bootLayer = findBootLayer();

        for (String moduleName : List.of(LauncherConstants.MODULE_LAUNCHER, LauncherConstants.MODULE_SPI, LauncherConstants.MODULE_JAR)) {
            if (bootLayer.findModule(moduleName).isEmpty()) {
                System.err.println("Invalid module path configuration.");
                System.err.println("The module " + moduleName + " was not found in the boot module layer.");
                throw new Error("Missing module: " + moduleName);
            }
        }
        if (Main.class.getModule() != bootLayer.findModule(LauncherConstants.MODULE_LAUNCHER).orElse(null)) {
            System.err.println("Invalid module path configuration.");
            System.err.println("The currently running main class is not loaded as part of the " + LauncherConstants.MODULE_LAUNCHER + " module.");
            throw new Error("Wrong main module: " + Main.class.getModule().getName());
        }

        JavaBaseAccess.get(); // verify that we can access the trusted lookup

        BootstrapStreamHandlerFactory streamHandlerFactory = new BootstrapStreamHandlerFactory(bootLayer);
        URL.setURLStreamHandlerFactory(streamHandlerFactory);

        List<BootPlugin> plugins = ServiceLoader.load(bootLayer, BootPlugin.class).stream().map(ServiceLoader.Provider::get).toList();

        Set<String> rootModules = new HashSet<>();
        List<ModuleFinder> moduleFinders = new ArrayList<>();
        for (BootPlugin plugin : plugins) {
            String pluginName = plugin.name();
            try {
                plugin.initialize(bootLayer);
                rootModules.addAll(plugin.rootModules());
                moduleFinders.add(plugin.findModules());
            } catch (Exception e) {
                throw new Error("Error loading boot plugin " + pluginName, e);
            }
        }
        ModuleFinder bootstrapModuleFinder = new LatestVersionModuleFinder(moduleFinders);
        Configuration bootstrapConfiguration = bootLayer.configuration().resolveAndBind(bootstrapModuleFinder, ModuleFinder.of(), rootModules);
        if (bootstrapConfiguration.modules().isEmpty()) {
            throw new Error("The computed bootstrap layer is empty.");
        }

        ModuleLoaderPool.Controller bootstrapController = ModuleLoaderPool.defineWithOneLoader("bootstrap", bootstrapConfiguration, List.of(bootLayer), ClassTransformer.noop());
        ModuleSystem system = new ModuleSystemImpl(bootLayer, bootstrapController.layerController());

        // We only define a single loader, it can be retrieved by any module on the bootstrap layer.
        ClassLoader contextLoader = bootstrapController.pool().apply(bootstrapConfiguration.modules().iterator().next().name());

        Map<String, Entrypoint> entrypointMap = new HashMap<>();
        for (Entrypoint entrypoint : ServiceLoader.load(bootstrapController.layer(), Entrypoint.class).stream().map(ServiceLoader.Provider::get).toList()) {
            String name = entrypoint.name();
            if (entrypointMap.containsKey(name)) {
                throw new Error("Multiple entrypoints named " + name + ": " + entrypointMap.get(name).getClass().getName() + " and " + entrypoint.getClass().getName());
            }
            entrypointMap.put(name, entrypoint);
        }
        Entrypoint entrypointToLaunch;
        String entrypointName = System.getProperty(LauncherConstants.PROP_ENTRYPOINT);
        if (entrypointName != null) {
            entrypointToLaunch = entrypointMap.get(entrypointName);
            if (entrypointToLaunch == null) {
                throw new Error("Requested entrypoint not found: " + entrypointName);
            }
        } else if (entrypointMap.size() == 1) {
            entrypointToLaunch = entrypointMap.values().iterator().next();
        } else if (entrypointMap.isEmpty()) {
            throw new Error("No entrypoints found in bootstrap layer.");
        } else {
            throw new Error("Multiple entrypoints found in bootstrap layer. Specify " + LauncherConstants.PROP_ENTRYPOINT + " to select an entrypoint. Detected entrypoints: " + String.join(", ", entrypointMap.keySet().stream().sorted().toList()));
        }

        streamHandlerFactory.loadProtocolProviders(bootstrapController.layer());
        for (BootPlugin plugin : plugins) plugin.consumeLayer(bootstrapController.layer());

        Thread.currentThread().setContextClassLoader(contextLoader);
        entrypointToLaunch.main(system, args);
    }

    private static ModuleLayer findBootLayer() {
        ModuleLayer bootLayer = ModuleLayer.boot();
        if (System.getProperties().get(ClassLoader.getSystemClassLoader()) instanceof ModuleLayer layer) {
            bootLayer = layer;
        }
        System.getProperties().remove(ClassLoader.getSystemClassLoader());
        return bootLayer;
    }
}
