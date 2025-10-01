package bootstrap.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.*;
import java.lang.reflect.Constructor;
import java.net.*;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LegacyLauncher extends URLClassLoader {

    private static final String BOOTSTRAP_LAUNCHER_MODULE = "bootstrap.launcher";
    private static final String BOOTSTRAP_SPI_MODULE = "bootstrap.spi";
    private static final String BOOTSTRAP_JAR_MODULE = "bootstrap.jar";
    private static final List<String> BOOTSTRAP_MODULES = List.of(
            BOOTSTRAP_LAUNCHER_MODULE,
            BOOTSTRAP_SPI_MODULE,
            BOOTSTRAP_JAR_MODULE
    );

    private static final String BOOT_PLUGIN_CLASS = "bootstrap.spi.BootPlugin";

    private final Map<String, JarModuleReference> moduleMap;
    private final Map<String, String> packageMap;

    @SuppressWarnings("unused")
    public LegacyLauncher(ClassLoader appClassLoader) {
        super(new URL[0], getParentClassLoaderAndWarn(appClassLoader));
        Thread.currentThread().setContextClassLoader(appClassLoader);
        if (isLegacyLaunch()) {
            try {
                this.moduleMap = this.initializeLegacyModuleMap();
                this.packageMap = this.initializeLegacyPackageMap(this.moduleMap);
                ModuleLayer layer = this.initializeLegacyModuleLayer(this.moduleMap);
                this.emulateAddOpens(layer);
                // (ab)use the system properties to get the module layer to Main
                System.getProperties().put(this, layer);
            } catch (Exception e) {
                throw new Error("Failed to setup legacy boot layer.", e);
            }
        } else {
            this.moduleMap = Map.of();
            this.packageMap = Map.of();
        }
        Thread.currentThread().setContextClassLoader(this);
    }

    private Map<String, JarModuleReference> initializeLegacyModuleMap() throws IOException {
        List<String> cp = List.of(Objects.requireNonNullElse(System.getProperty("java.class.path"), "").split(Pattern.quote(File.pathSeparator), -1));
        List<String> legacyClassPath = new ArrayList<>();
        List<String> modularClassPath = new ArrayList<>();
        Map<String, JarModuleReference> modules = new HashMap<>();
        for (String cpEntry : cp) {
            if (cpEntry.isEmpty()) continue;
            File file = new File(cpEntry);
            if (file.isDirectory()) {
                legacyClassPath.add(cpEntry);
            } else {
                Predicate<ModuleDescriptor> isBootstrapModule = descriptor -> BOOTSTRAP_MODULES.contains(descriptor.name());
                Predicate<ModuleDescriptor> isLaunchPlugin = descriptor -> descriptor.provides().stream().anyMatch(provides -> BOOT_PLUGIN_CLASS.equals(provides.service()) && !provides.providers().isEmpty());
                Optional<JarModuleReference> reference = JarModuleReference.createReference(file, isBootstrapModule.or(isLaunchPlugin));
                if (reference.isPresent()) {
                    ModuleReference prev = modules.put(reference.get().descriptor().name(), reference.get());
                    if (prev != null) {
                        String moduleName = reference.get().descriptor().name();
                        String prevLocation = prev.location().map(URI::toString).orElse("<unknown>");
                        String thisLocation = reference.get().location().map(URI::toString).orElse("<unknown>");
                        throw new IllegalStateException("Two versions of module " + moduleName +  " on classpath: " + prevLocation + " and " + thisLocation);
                    }
                    modularClassPath.add(cpEntry);
                } else {
                    legacyClassPath.add(cpEntry);
                }
            }
        }
        for (String requiredModule : BOOTSTRAP_MODULES) {
            if (!modules.containsKey(requiredModule)) {
                throw new NoSuchElementException("Required module for legacy boot layer missing: " + requiredModule);
            }
        }

        System.getProperties().setProperty("java.class.path", String.join(File.pathSeparator, modularClassPath));
        System.getProperties().setProperty("bootstrap.classpath", String.join(File.pathSeparator, legacyClassPath));

        return Map.copyOf(modules);
    }

    private Map<String, String> initializeLegacyPackageMap(Map<String, JarModuleReference> modules) {
        Map<String, String> packages = new HashMap<>();
        for (String moduleName : modules.keySet()) {
            for (String pkg : modules.get(moduleName).descriptor().packages()) {
                String prev = packages.put(pkg, moduleName);
                if (prev != null) {
                    throw new LayerInstantiationException("Split packages: Modules " + prev + " and " + moduleName + " share package " + pkg);
                }
            }
        }
        return Map.copyOf(packages);
    }

    private ModuleLayer initializeLegacyModuleLayer(Map<String, JarModuleReference> modules) {
        ModuleFinder before = new LegacyModuleFinder(modules);
        ModuleFinder after = new LegacyModuleFinder(Map.of());
        Configuration configuration = ModuleLayer.boot().configuration().resolveAndBind(before, after, BOOTSTRAP_MODULES);
        ModuleLayer.Controller controller = ModuleLayer.defineModules(configuration, List.of(ModuleLayer.boot()), moduleName -> this);
        return controller.layer();
    }

    private void emulateAddOpens(ModuleLayer legacyLayer) {
        Module javaBase = Object.class.getModule();
        Module bootstrapJar = legacyLayer.findModule(BOOTSTRAP_JAR_MODULE).orElse(null);
        if (bootstrapJar == null) return;
        Module unnamedModule = this.getClass().getModule();
        if (!javaBase.isOpen("java.lang", unnamedModule)) {
            throw new Error("Invalid classpath configuration. Update your java arguments to include --add-opens java.base/java.lang=ALL-UNNAMED");
        }
        try {
            Constructor<ModuleLayer.Controller> controllerConstructor = ModuleLayer.Controller.class.getDeclaredConstructor(ModuleLayer.class);
            controllerConstructor.setAccessible(true);
            ModuleLayer.Controller bootController = controllerConstructor.newInstance(ModuleLayer.boot());
            bootController.addOpens(javaBase, "java.lang.invoke", bootstrapJar);
        } catch (Exception e) {
            throw new RuntimeException("Failed to emulate --add-opens on the legacy boot layer.", e);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        int idx = name.lastIndexOf('.');
        if (idx < 0) return super.findClass(name);
        String pkg = name.substring(0, idx);
        if (this.packageMap.containsKey(pkg)) {
            Class<?> cls = this.findClass(this.packageMap.get(pkg), name);
            if (cls == null) throw new ClassNotFoundException(name);
            return cls;
        }
        return super.findClass(name);
    }

    @Override
    protected Class<?> findClass(String moduleName, String name) {
        try {
            if (moduleName == null) return super.findClass(null, name);
            URL url = this.findResource(moduleName, name.replace('.', '/') + ".class");
            if (url == null) return null;
            byte[] classBytes;
            try (InputStream in = url.openStream()) { classBytes = in.readAllBytes(); }
            return this.defineClass(name, classBytes, 0, classBytes.length);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        if (moduleName == null) return super.findResource(null, name);
        JarModuleReference ref = this.moduleMap.get(moduleName);
        if (ref == null) return null;
        URI uri = ref.fileIndex().get(JarModuleReader.normalize(name));
        return uri == null ? null : uri.toURL();
    }

    @SuppressWarnings("unused") // Used by the instrumentation API
    void appendToClassPathForInstrumentation(String path) {
        try {
            this.addURL(new URI("file", path, null).toURL());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected PermissionCollection getPermissions(CodeSource codeSource) {
        Permissions permissions = new Permissions();
        permissions.add(new AllPermission());
        permissions.setReadOnly();
        return permissions;
    }

    private static boolean isLegacyLaunch() {
        return LegacyLauncher.class.getModule() == null || !LegacyLauncher.class.getModule().isNamed();
    }

    private static ClassLoader getParentClassLoaderAndWarn(ClassLoader appClassLoader) {
        if (!isLegacyLaunch()) {
            System.err.println("Modular environment detected. LegacyLauncher is not required.");
            System.err.println("Replacing the system classloader from a named module is unsupported and may");
            System.err.println("cause problems. Consider removing the java.system.class.loader property.");
            return appClassLoader;
        } else {
            return ClassLoader.getPlatformClassLoader();
        }
    }

    private static class LegacyModuleFinder implements ModuleFinder {

        private final Map<String, ModuleReference> moduleMap;
        private final Set<ModuleReference> allModules;

        private LegacyModuleFinder(Map<String, ? extends ModuleReference> references) {
            this.moduleMap = Map.copyOf(references);
            this.allModules = Set.copyOf(this.moduleMap.values());
        }

        @Override
        public Optional<ModuleReference> find(String name) {
            return Optional.ofNullable(this.moduleMap.get(name));
        }

        @Override
        public Set<ModuleReference> findAll() {
            return this.allModules;
        }
    }

    private static class JarModuleReference extends ModuleReference {

        private final File file;
        private final Map<String, URI> index;

        private JarModuleReference(ModuleDescriptor descriptor, File file) throws IOException {
            super(descriptor, file.toURI());
            this.file = file;
            try (JarModuleReader reader = this.open()) {
                this.index = reader.list()
                        .filter(entry -> !entry.endsWith("/"))
                        .map(JarModuleReader::normalize)
                        .flatMap(key -> reader.find(key).stream().map(value -> Map.entry(key, value)))
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        }

        public Map<String, URI> fileIndex() {
            return this.index;
        }

        @Override
        public JarModuleReader open() throws IOException {
            return new JarModuleReader(this.file);
        }

        public static Optional<JarModuleReference> createReference(File file, Predicate<ModuleDescriptor> allowedModules) throws IOException {
            Optional<ModuleDescriptor> descriptor = readModuleDescriptor(file);
            if (descriptor.isEmpty() || !allowedModules.test(descriptor.get())) return Optional.empty();
            return Optional.of(new JarModuleReference(descriptor.get(), file));
        }

        private static Optional<ModuleDescriptor> readModuleDescriptor(File file) throws IOException {
            try (ModuleReader reader = new JarModuleReader(file)) {
                Optional<InputStream> maybeModuleInfo = reader.open("module-info.class");
                if (maybeModuleInfo.isEmpty()) return Optional.empty();
                ModuleDescriptor descriptor;
                try (InputStream moduleInfo = maybeModuleInfo.get()) {
                    descriptor = ModuleDescriptor.read(moduleInfo);
                }
                if (descriptor.isAutomatic()) return Optional.empty();
                Set<String> packages = reader.list()
                        .map(JarModuleReader::normalize)
                        .filter(entry -> entry.endsWith(".class"))
                        .filter(entry -> entry.contains("/"))
                        .map(entry -> entry.substring(0, entry.lastIndexOf('/')))
                        .map(entry -> entry.replace('/', '.'))
                        .collect(Collectors.toUnmodifiableSet());
                ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(descriptor.name(), descriptor.modifiers());
                builder.packages(descriptor.packages()).packages(packages);
                for (ModuleDescriptor.Exports exports : descriptor.exports()) builder.exports(exports);
                if (!descriptor.isOpen()) for (ModuleDescriptor.Opens opens : descriptor.opens()) builder.opens(opens);
                for (ModuleDescriptor.Requires requires : descriptor.requires()) builder.requires(requires);
                for (String uses : descriptor.uses()) builder.uses(uses);
                for (ModuleDescriptor.Provides provides : descriptor.provides()) builder.provides(provides);
                return Optional.of(builder.build());
            }
        }
    }

    private static class JarModuleReader implements ModuleReader {

        private final URI location;
        private final JarFile jar;
        private final Map<String, JarEntry> entryMap;

        private JarModuleReader(File file) throws IOException {
            this(file.toURI(), new JarFile(file, true, ZipFile.OPEN_READ, Runtime.version()));
        }

        private JarModuleReader(URI location, JarFile jar) {
            this.location = location;
            this.jar = jar;
            this.entryMap = jar.versionedStream().collect(Collectors.toUnmodifiableMap(
                    je -> normalize(je.getName()),
                    Function.identity(),
                    (je1, je2) -> je1
            ));
        }

        @Override
        public Optional<URI> find(String name) {
            JarEntry je = this.entryMap.get(normalize(name));
            if (je == null) return Optional.empty();
            String jarLocation = "jar:" + this.location.toString().replace("!", "%21");
            String fileLocation = je.getName().replaceAll("^/+", "");
            return Optional.of(URI.create(jarLocation + "!/" + fileLocation));
        }

        @Override
        public Stream<String> list() {
            return this.entryMap.values().stream().map(ZipEntry::getName);
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            // We can't use the default implementation to not load filesystem providers too early.
            JarEntry je = this.entryMap.get(normalize(name));
            if (je == null) return Optional.empty();
            return Optional.of(this.jar.getInputStream(je));
        }

        @Override
        public void close() throws IOException {
            this.jar.close();
        }

        public static String normalize(String path) {
            return path.replace("\\", "/").replaceAll("/+", "/").replaceAll("(^/)|(/$)", "");
        }
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }
}
