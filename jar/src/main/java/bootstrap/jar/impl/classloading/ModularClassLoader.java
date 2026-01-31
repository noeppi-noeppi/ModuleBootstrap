package bootstrap.jar.impl.classloading;

import bootstrap.jar.classloading.ClassTransformer;
import bootstrap.jar.impl.JarModuleReference;
import bootstrap.jar.impl.reflect.JavaBaseAccess;
import bootstrap.jar.util.FlatteningEnumeration;
import bootstrap.jar.util.NameHelper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

@NotNullByDefault
public class ModularClassLoader extends SecureClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final JavaBaseAccess jba;
    private final ClassLoader fallback;
    private final LoaderPoolImpl pool;
    private final Map<String, ResolvedModule> moduleMap;
    private final Map<String, Manifest> manifestMap;
    private final Map<String, CodeSource> codeSourceMap;
    private final Map<String, String> packageMap;
    private final RuntimePackageMap runtimePackageMap;

    public ModularClassLoader(String name, LoaderPoolImpl pool, Configuration configuration, Set<String> modules, ClassLoader fallback) {
        super(name, Objects.requireNonNull(fallback));
        this.jba = JavaBaseAccess.get();
        this.fallback = Objects.requireNonNull(fallback);
        this.pool = Objects.requireNonNull(pool);
        this.moduleMap = configuration.modules().stream()
                .filter(rm -> modules.contains(rm.name()))
                .collect(Collectors.toUnmodifiableMap(
                        ResolvedModule::name,
                        Function.identity()
                ));
        for (String moduleName : modules) {
            if (!this.moduleMap.containsKey(moduleName)) {
                throw new LayerInstantiationException("Module not found in configuration: " + moduleName);
            }
        }

        Map<String, Manifest> manifestMap = new HashMap<>();
        Map<String, CodeSource> codeSourceMap = new HashMap<>();
        for (ResolvedModule module : this.moduleMap.values()) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
            if (module.reference() instanceof JarModuleReference jar) {
                manifest = jar.manifest();
            } else try (
                    ModuleReader reader = module.reference().open();
                    @Nullable InputStream in = reader.open("META-INF/MANIFEST.MF").orElse(null)
            ) {
                if (in != null) manifest = new Manifest(in);
            } catch (IOException e) {
                //
            }

            module.reference().location().ifPresent(uri -> {
                try {
                    CodeSource codeSource = new CodeSource(uri.toURL(), (CodeSigner[]) null);
                    codeSourceMap.put(module.name(), codeSource);
                } catch (MalformedURLException e) {
                    //
                }
            });


            manifestMap.put(module.name(), manifest);
        }
        this.manifestMap = Map.copyOf(manifestMap);
        this.codeSourceMap = Map.copyOf(codeSourceMap);

        Map<String, String> packageMap = new HashMap<>();
        BiConsumer<String, String> addPackage = (pkg, moduleName) -> {
            String prev = packageMap.put(pkg, moduleName);
            if (prev != null && !Objects.equals(prev, moduleName)) {
                throw new LayerInstantiationException("Split packages: Modules " + prev + " and " + moduleName + " share package " + pkg);
            }
        };
        for (ResolvedModule mod : this.moduleMap.values()) {
            mod.reference().descriptor().packages().forEach(pkg -> addPackage.accept(pkg, mod.name()));
            for (ResolvedModule dep : mod.reads()) {
                dep.reference().descriptor().packages().forEach(pkg -> addPackage.accept(pkg, dep.name()));
            }
        }
        this.packageMap = Map.copyOf(packageMap);
        this.runtimePackageMap = new RuntimePackageMap(this, pool, this.packageMap);
    }

    public Optional<String> getModuleNameFromPackage(String pkg) {
        return Optional.ofNullable(this.packageMap.get(pkg));
    }

    // Only returns package if it's newly defined.
    private @Nullable Package maybeDefinePackageForClass(String className) {
        int idx = className.lastIndexOf('.');
        if (idx < 0) return null;
        String pkg = className.substring(0, idx);

        synchronized (this.getClassLoadingLock(pkg + ".package-info")) {
            if (this.getDefinedPackage(pkg) != null) return null;
            String moduleName = this.packageMap.get(pkg);
            Manifest manifest = moduleName == null ? null : this.manifestMap.get(moduleName);
            if (manifest == null) return null;

            String specTitle = null, specVersion = null, specVendor = null;
            String implTitle = null, implVersion = null, implVendor = null;

            for (Attributes attributes : new Attributes[]{ manifest.getMainAttributes(), manifest.getAttributes(pkg) }) {
                if (attributes == null) continue;
                specTitle = nonNullOrDefault(attributes.getValue(Attributes.Name.SPECIFICATION_TITLE), specTitle);
                specVersion = nonNullOrDefault(attributes.getValue(Attributes.Name.SPECIFICATION_VERSION), specVersion);
                specVendor = nonNullOrDefault(attributes.getValue(Attributes.Name.SPECIFICATION_VENDOR), specVendor);
                implTitle = nonNullOrDefault(attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE), implTitle);
                implVersion = nonNullOrDefault(attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION), implVersion);
                implVendor = nonNullOrDefault(attributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR), implVendor);
            }

            return this.definePackage(pkg, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, null);
        }
    }

    @Override
    public final Class<?> loadClass(String name) throws ClassNotFoundException {
        return this.loadClass(name, false);
    }

    @Override
    protected final Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // synchronize to the interrupt lock, so interrupts block until the classloading is complete.
        synchronized (this.jba.getThreadInterruptLock(Thread.currentThread())) {
            return this.safeLoadClass(name, resolve);
        }
    }

    protected Class<?> safeLoadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> cls = this.findLoadedClass(name);
            if (cls == null) {
                try {
                    cls = this.findClass(name);
                } catch (ClassNotFoundException e) {
                    cls = this.loadClassFromParentLayers(name);
                }
            }
            if (resolve) {
                this.resolveClass(cls);
            }
            return cls;
        }
    }

    private Class<?> loadClassFromParentLayers(String className) throws ClassNotFoundException {
        int idx = className.lastIndexOf('.');
        @Nullable String pkg = idx < 0 ? null : className.substring(0, idx);
        if (pkg != null && this.packageMap.containsKey(pkg)) {
            try {
                return this.pool.loadParentClass(this.packageMap.get(pkg), className);
            } catch (ClassNotFoundException e) {
                //
            }
        } else if (pkg != null) {
            Optional<Module> foreign = this.runtimePackageMap.getRuntimePackage(pkg);
            if (foreign.isPresent()) try {
                return foreign.get().getClassLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                //
            }
        }
        return this.fallback.loadClass(className);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        int idx = name.lastIndexOf('.');
        if (idx < 0) throw new ClassNotFoundException(name);
        String pkg = name.substring(0, idx);
        @Nullable String moduleName = this.packageMap.get(pkg);
        if (moduleName == null) throw new ClassNotFoundException(name);
        @Nullable Class<?> cls = this.findClass(moduleName, name);
        if (cls == null) throw new ClassNotFoundException(name);
        return cls;
    }

    @Override
    protected @Nullable Class<?> findClass(@Nullable String moduleName, String className) {
        if (moduleName == null) return null;
        if (!this.moduleMap.containsKey(moduleName)) return null;

        int idx = className.lastIndexOf('.');
        if (idx < 0 || !Objects.equals(moduleName, this.packageMap.get(className.substring(0, idx)))) return null;

        byte[] data;
        try {
            data = this.pool.getTransformedClass(moduleName, className, ClassTransformer.REASON_CLASSLOADING, false);
        } catch (ClassNotFoundException e) {
            return null;
        }
        Package pkg = this.maybeDefinePackageForClass(className);
        Class<?> cls = this.defineClass(className, data, 0, data.length, this.codeSourceMap.get(moduleName));
        if (pkg != null && cls.getModule().isNamed()) this.jba.assignPackageToModule(pkg, cls.getModule());
        return cls;
    }

    @Override
    public @Nullable URL getResource(String name) {
        Objects.requireNonNull(name);
        @Nullable URL url = this.findResource(name);
        if (url != null) return url;
        return this.fallback.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> ownResources = this.findResources(name);
        Enumeration<URL> fallbackResources = this.fallback.getResources(name);
        return new FlatteningEnumeration<>(Collections.enumeration(List.of(ownResources, fallbackResources)));
    }

    private @Nullable String packageForResource(String resource) {
        int idx = resource.lastIndexOf('/');
        if (idx < 0) return null;
        String dir = resource.substring(0, idx).replaceAll("(^/+)|(/+$)|(/+(?=/))", "");
        if (dir.indexOf('.') >= 0) return null;
        return dir.replace('/', '.');
    }

    private boolean isEncapsulated(String moduleName, String resource) {
        if (resource.endsWith(".class")) return false;
        @Nullable ResolvedModule module = this.moduleMap.get(moduleName);
        @Nullable String pkg = this.packageForResource(resource);
        if (module == null || pkg == null) return false;
        if (!NameHelper.validTypeName(pkg)) return false;
        ModuleDescriptor descriptor = module.reference().descriptor();
        if (descriptor.isOpen() || descriptor.isAutomatic()) return false;
        return descriptor.opens().stream().noneMatch(opens -> !opens.isQualified() && Objects.equals(pkg, opens.source()));
    }

    @Override
    protected @Nullable URL findResource(String name) {
        @Nullable String pkg = this.packageForResource(name);
        if (pkg == null) return null;
        @Nullable String moduleName = this.packageMap.get(pkg);
        if (moduleName != null) {
            if (this.isEncapsulated(moduleName, pkg)) return null;
            try {
                return this.findResource(moduleName, name);
            } catch (IOException e) {
                return null;
            }
        } else {
            String normalizedName = name.replaceAll("(^/+)|(/+$)|(/+(?=/))", "");
            if (normalizedName.startsWith("META-INF/") && !normalizedName.equals("META-INF/MANIFEST.MF")) {
                // META-INF is not exclusively owned by a module. However, few libraries load their own resources directly
                // from the classloader instead of using Class#getResource. Therefore we return the unambiguous resources here
                List<URL> candidates = new ArrayList<>();
                for (String mod : this.moduleMap.keySet()) {
                    try {
                        URL resource = this.findResource(mod, name);
                        if (resource != null) candidates.add(resource);
                    } catch (IOException e) {
                        //
                    }
                }
                if (candidates.size() == 1) return candidates.getFirst();
                return null;
            }
            return null;
        }
    }

    @Override
    protected @Nullable URL findResource(@Nullable String moduleName, String name) throws IOException {
        if (moduleName == null) return null;
        if (!this.moduleMap.containsKey(moduleName)) return null;
        return this.pool.findResource(moduleName, name).orElse(null);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<URL> resources = new ArrayList<>(1);
        for (String moduleName : this.moduleMap.keySet()) {
            URL url = this.findResource(moduleName, name);
            if (url == null || this.isEncapsulated(moduleName, name)) continue;
            resources.add(url);
        }
        return Collections.enumeration(resources);
    }

    @Override
    protected PermissionCollection getPermissions(CodeSource codesource) {
        Permissions permissions = new Permissions();
        permissions.add(new AllPermission());
        permissions.setReadOnly();
        return permissions;
    }
    
    public void addModuleReads(ModuleLayer.Controller layerController, Module source, Module target) {
        this.runtimePackageMap.addModuleReads(layerController, source, target);
    }
    
    public Optional<byte[]> getManifestData(String moduleName) {
        Manifest manifest = this.manifestMap.get(moduleName);
        if (manifest == null) return Optional.empty();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            manifest.write(out);
            out.close();
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(out.toByteArray());
    }

    @Nullable
    @Contract("_, !null -> !null")
    private static <T> T nonNullOrDefault(@Nullable T value, @Nullable T dfl) {
        return value == null ? dfl : value;
    }
}
