package bootstrap.jar.impl.classloading;

import bootstrap.jar.classloading.ClassDiscovery;
import bootstrap.jar.classloading.ClassTransformer;
import bootstrap.jar.classloading.ModuleLoaderPool;
import bootstrap.jar.classloading.TransformingEnvironment;
import bootstrap.jar.impl.reflect.JavaBaseAccess;
import bootstrap.jar.url.classtransformer.ClassTransformerStreamHandler;
import bootstrap.jar.util.NameHelper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@NotNullByDefault
public class LoaderPoolImpl implements ModuleLoaderPool {

    private final String name;
    private final Configuration configuration;
    private final ClassTransformer transformer;
    private final RuntimeClassMap runtimeClassMap;
    private final Map<String, ModuleContext> moduleMap;
    private final List<ModuleLayer> parentLayers;
    private final String resourceIdentifier;

    public LoaderPoolImpl(String name, Configuration configuration, List<ModuleLayer> parentLayers, ClassTransformer transformer, Function<String, String> cluster, ClassLoader fallback) {
        this.name = name;
        this.configuration = configuration;
        this.transformer = transformer;
        this.runtimeClassMap = new RuntimeClassMap();
        this.moduleMap = this.buildModuleMap(cluster, fallback);
        this.parentLayers = List.copyOf(parentLayers);
        this.checkParentLayers(configuration, this.parentLayers);
        this.resourceIdentifier = ClassTransformerStreamHandler.registerPool(name, this);

        List<? extends ClassLoader> loaders = this.moduleMap.values().stream().map(ModuleContext::loader).distinct().toList();
        this.bindToParentLayers(parentLayers, loaders, new HashSet<>());
    }

    private Map<String, ModuleContext> buildModuleMap(Function<String, String> cluster, ClassLoader fallback) {
        try {
            Map<String, Set<ResolvedModule>> clusterMap = new HashMap<>();
            for (ResolvedModule module : this.configuration.modules()) {
                clusterMap.computeIfAbsent(cluster.apply(module.name()), k -> new HashSet<>()).add(module);
            }
            Map<String, ModuleContext> moduleMap = new HashMap<>();
            for (String clusterId : clusterMap.keySet()) {
                Set<ResolvedModule> modules = Set.copyOf(clusterMap.get(clusterId));
                Set<String> moduleNames = modules.stream().map(ResolvedModule::name).collect(Collectors.toUnmodifiableSet());
                ModularClassLoader loader = new ModularClassLoader(this.name, this, this.configuration, moduleNames, fallback);
                for (ResolvedModule module : modules) {
                    TransformingEnvironment env = new TransformingEnvironmentImpl(module, loader);
                    ModuleContext context = new ModuleContext(module, module.reference().open(), loader, env);
                    moduleMap.put(module.name(), context);
                }
            }
            return Map.copyOf(moduleMap);
        } catch (IOException e) {
            throw new LayerInstantiationException("Failed to read modules", e);
        }
    }

    private void checkParentLayers(Configuration configuration, List<ModuleLayer> parentLayers) {
        int len = configuration.parents().size();
        if (parentLayers.size() != len) {
            throw new LayerInstantiationException("Failed to create loader pool: Parent layers don't match the configuration.");
        }
        for (int i = 0; i < len; i++) {
            if (parentLayers.get(i).configuration() != configuration.parents().get(i)) {
                throw new LayerInstantiationException("Failed to create loader pool: Parent layers don't match the configuration.");
            }
        }
    }

    private void bindToParentLayers(List<? extends ModuleLayer> parentLayers, List<? extends ClassLoader> loaders, Set<ModuleLayer> knownLayers) {
        JavaBaseAccess jba = JavaBaseAccess.get();
        for (ModuleLayer parent : parentLayers) {
            if (parent.modules().isEmpty() && parent.parents().isEmpty()) {
                // Empty layer
                continue;
            }
            if (knownLayers.add(parent)) {
                for (ClassLoader loader : loaders) {
                    jba.bindLayerToLoader(parent, loader);
                }
                this.bindToParentLayers(parent.parents(), loaders, knownLayers);
            }
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public Configuration configuration() {
        return this.configuration;
    }

    @Override
    public ClassLoader apply(String moduleName) {
        ModuleContext context = this.moduleMap.get(moduleName);
        if (context != null) return context.loader();
        throw new IllegalArgumentException("Module " + moduleName + " is not part of this loader pool.");
    }
    
    public @Nullable ModularClassLoader getClassLoaderOrNull(@Nullable String moduleName) {
        if (moduleName == null) return null;
        ModuleContext context = this.moduleMap.get(moduleName);
        return context != null ? context.loader() : null;
    }

    @Override
    public ClassDiscovery discovery(String moduleName) {
        ModuleContext context = this.moduleMap.get(moduleName);
        if (context != null) return context.env();
        throw new IllegalArgumentException("Module " + moduleName + " is not part of this loader pool.");
    }

    @Override
    public void addRuntimeClass(String moduleName, String className, URL resource) {
        ModuleContext context = this.moduleMap.get(moduleName);
        if (context == null) {
            throw new IllegalStateException("Can't add runtime class " + className + ": Module " + moduleName + " is not part of this loader pool.");
        }
        int idx = className.lastIndexOf('.');
        String pkg = className.substring(0, idx);
        if (!context.module().reference().descriptor().packages().contains(pkg)) {
            throw new IllegalStateException("Can't add runtime class " + className + ": Package is not part of module " + moduleName + ".");
        }
        this.runtimeClassMap.addRuntimeClass(moduleName, className, resource);
    }

    private ClassResource findClassURL(String moduleName, String className) throws ClassNotFoundException {
        try {
            if (!NameHelper.validLoadableClassName(className)) throw new ClassNotFoundException(className);
            String resource = className.replace('.', '/') + ".class";
            Optional<URL> url = this.findNonTransformedResource(moduleName, resource);
            if (url.isPresent()) return new ClassResource(url.get(), true);
            url = this.runtimeClassMap.getRuntimeClass(moduleName, className);
            if (url.isPresent()) return new ClassResource(url.get(), false);
            throw new ClassNotFoundException(className);
        } catch (IOException e) {
            throw new ClassNotFoundException(className, e);
        }
    }

    public byte[] getTransformedClass(String moduleName, String className, String reason, boolean searchParents) throws ClassNotFoundException {
        if (!NameHelper.validTypeName(moduleName) || !NameHelper.validLoadableClassName(className)) throw new ClassNotFoundException(className);
        ModuleContext context = this.moduleMap.get(moduleName);
        if (context != null) {
            ClassResource res = this.findClassURL(moduleName, className);
            byte[] data;
            try (InputStream in = res.url().openStream()) {
                data = in.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(className, e);
            }
            if (data.length == 0) throw new ClassNotFoundException(className);
            if (res.needsTransform()) {
                data = this.transformer.transformClass(context.env(), moduleName, className, data, reason);
            }
            if (data.length == 0) throw new ClassNotFoundException(className);
            return data;
        } else if (searchParents) {
            Module parentModule = this.findParentModule(moduleName);
            if (parentModule != null) {
                try (InputStream in = parentModule.getResourceAsStream(className.replace('.', '/') + ".class")) {
                    if (in != null) return in.readAllBytes();
                } catch (IOException e) {
                    throw new ClassNotFoundException(className, e);
                }
            }
        }
        throw new ClassNotFoundException(className);
    }

    public Class<?> loadParentClass(String moduleName, String className) throws ClassNotFoundException {
        if (!NameHelper.validTypeName(moduleName) || !NameHelper.validLoadableClassName(className)) throw new ClassNotFoundException(className);

        int idx = className.lastIndexOf('.');
        if (idx < 0) throw new ClassNotFoundException(className);
        String pkg = className.substring(0, idx);

        Module parentModule = this.findParentModule(moduleName);
        if (parentModule == null || !parentModule.getPackages().contains(pkg)) {
            throw new ClassNotFoundException(className);
        }

        ClassLoader loader = Objects.requireNonNullElse(parentModule.getClassLoader(), ClassLoader.getPlatformClassLoader());
        return loader.loadClass(className);
    }

    public Optional<URL> findResource(String moduleName, String resource) throws IOException {
        resource = resource.replaceAll("(^/+)|(/+$)|(/(?=/+))", "");
        String internalName;
        String className;
        if (resource.endsWith(".class") && this.moduleMap.containsKey(moduleName)
                && (internalName = resource.substring(0, resource.length() - 6)).indexOf('.') < 0
                && NameHelper.validLoadableClassName(className = internalName.replace('/', '.'))) {
            try {
                this.getTransformedClass(moduleName, className, ClassTransformer.REASON_RESOURCE, false);
                return Optional.of(new URI(ClassTransformerStreamHandler.PROTOCOL,
                        this.resourceIdentifier, "/" + moduleName + "/" + className, null
                ).toURL());
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            } catch (URISyntaxException e) {
                throw new IOException("Invalid URI", e);
            }
        } else if (resource.equals("META-INF/MANIFEST.MF")) {
            try {
                return Optional.of(new URI(ClassTransformerStreamHandler.PROTOCOL,
                        this.resourceIdentifier, "/" + moduleName + "/META-INF.MANIFEST", null
                ).toURL());
            } catch (URISyntaxException e) {
                throw new IOException("Invalid URI", e);
            }
        } else {
            return this.findNonTransformedResource(moduleName, resource);
        }
    }

    public Optional<URL> findNonTransformedResource(String moduleName, String resource) throws IOException {
        resource = resource.replaceAll("(^/+)|(/+$)|(/(?=/+))", "");
        ModuleContext context = this.moduleMap.get(moduleName);
        if (context == null) return Optional.empty();
        URI uri = context.reader.find(resource).orElse(null);
        if (uri == null) return Optional.empty();
        return Optional.of(uri.toURL());
    }

    private @Nullable Module findParentModule(String moduleName) {
        if (this.moduleMap.containsKey(moduleName)) return null;
        for (ModuleLayer layer : this.parentLayers) {
            Optional<Module> module = layer.findModule(moduleName);
            if (module.isPresent()) return module.get();
        }
        return null;
    }

    private record ClassResource(URL url, boolean needsTransform) {}
    private record ModuleContext(ResolvedModule module, ModuleReader reader, ModularClassLoader loader, TransformingEnvironment env) {}

    private class TransformingEnvironmentImpl implements TransformingEnvironment {

        private final ResolvedModule module;
        private final ModularClassLoader loader;

        private TransformingEnvironmentImpl(ResolvedModule module, ModularClassLoader loader) {
            this.module = module;
            this.loader = loader;
        }

        @Override
        public Optional<TransformingEnvironment> into(String moduleName) {
            return Optional.ofNullable(LoaderPoolImpl.this.moduleMap.get(moduleName)).map(ModuleContext::env);
        }

        @Override
        public ResolvedModule module() {
            return this.module;
        }

        @Override
        public ClassLoader loader() {
            return this.loader;
        }

        @Override
        public Optional<ResolvedModule> moduleForPackage(String packageName) {
            @Nullable String moduleName = this.loader.getModuleNameFromPackage(packageName).orElse(null);
            if (moduleName == null) return Optional.empty();

            // If the package is from our own module, return it.
            if (Objects.equals(this.module.name(), moduleName)) return Optional.of(this.module);

            // Check that the package is visible.
            return this.module.reads().stream()
                    .filter(rm -> Objects.equals(rm.name(), moduleName))
                    .findAny();
        }

        @Override
        public byte[] getTransformedClass(String className, String reason) throws ClassNotFoundException {
            int idx = className.lastIndexOf('.');
            if (idx < 0) throw new ClassNotFoundException(className);
            @Nullable ResolvedModule rm = this.moduleForPackage(className.substring(0, idx)).orElse(null);
            if (rm == null) throw new ClassNotFoundException(className);
            return LoaderPoolImpl.this.getTransformedClass(rm.name(), className, reason, true);
        }
    }
}
