package bootstrap.jar.impl;

import bootstrap.jar.JarMetadataFilter;
import bootstrap.jar.niofs.path.BasePath;
import bootstrap.jar.niofs.path.CompoundUriHelper;
import bootstrap.jar.util.ModuleHelper;
import bootstrap.jar.util.NameHelper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@NotNullByDefault
public class JarFactory {

    public static JarImpl create(JarMetadataFilter filter, List<Path> paths) throws IOException {
        paths = List.copyOf(paths);
        Path initialRoot = createInitialJarRoot(paths);
        Manifest initialManifest = resolveInitialManifest(initialRoot);

        List<Path> multiReleaseRoots = List.of();
        if (Boolean.parseBoolean(initialManifest.getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE.toString()))) {
            multiReleaseRoots = resolveMultiReleaseRoots(initialRoot, Math.max(8, Runtime.version().feature()));
        }
        
        FileSystem fs = buildJarFileSystem(initialRoot, multiReleaseRoots);
        ModuleDescriptor initialDescriptor;
        if (Files.isRegularFile(fs.getPath("/module-info.class"))) {
            try (InputStream in = Files.newInputStream(fs.getPath("/module-info.class"))) {
                initialDescriptor = ModuleDescriptor.read(in);
            }
        } else {
            initialDescriptor = createAutomaticModuleDescriptor(fs, initialManifest, filter, paths);
        }
        ModuleDescriptor extendedDescriptor = extendModuleDescriptor(initialDescriptor, fs, initialManifest);
        ModuleDescriptor finalDescriptor = filter.filterModuleDescriptor(extendedDescriptor, fs);

        setManifestAttributesFromDescriptor(initialManifest, finalDescriptor);

        Manifest finalManifest = (Manifest) filter.filterManifest(initialManifest, fs, finalDescriptor).clone();
        return new JarImpl(finalManifest, finalDescriptor, fs);
    }

    public static void setManifestAttributesFromDescriptor(Manifest manifest, ModuleDescriptor descriptor) {
        manifest.getMainAttributes().putValue(NameHelper.AUTOMATIC_MODULE_NAME.toString(), descriptor.name());
        if (descriptor.version().isPresent()) {
            manifest.getMainAttributes().putValue(Attributes.Name.IMPLEMENTATION_VERSION.toString(), descriptor.version().get().toString());
        }
        if (descriptor.mainClass().isPresent()) {
            manifest.getMainAttributes().putValue(Attributes.Name.MAIN_CLASS.toString(), descriptor.mainClass().get());
        } else {
            manifest.getMainAttributes().remove(Attributes.Name.MAIN_CLASS);
        }
    }

    private static Path createInitialJarRoot(List<Path> paths) throws IOException {
        if (paths.isEmpty()) throw new IllegalArgumentException("Jar file with no roots.");

        List<Path> dirs = new ArrayList<>(paths.size());
        for (Path path : paths) {
            if (!Files.exists(path)) {
                throw new NoSuchFileException("Nonexistent jar root: " + path.toUri());
            } else if (Files.isDirectory(path)) {
                dirs.add(path);
            } else {
                CompoundUriHelper.DeconstructedPath layerPath = new CompoundUriHelper.DeconstructedPath(List.of(path.toUri().toString()), "/");
                try {
                    dirs.add(Path.of(CompoundUriHelper.construct("layered", layerPath)));
                } catch (URISyntaxException e) {
                    throw new IOException("Failed to construct layered URI for jar root: " + path.toUri());
                }
            }
        }

        if (dirs.size() == 1) return dirs.getFirst();
        CompoundUriHelper.DeconstructedPath unionPath = new CompoundUriHelper.DeconstructedPath(dirs.stream().map(Path::toUri).map(URI::toString).toList(), "/");
        try {
            return Path.of(CompoundUriHelper.construct("union", unionPath));
        } catch (URISyntaxException e) {
            throw new IOException("Failed to construct union URI for jar root: " + unionPath);
        }
    }

    private static Manifest resolveInitialManifest(Path root) throws IOException {
        Path manifestPath = root.resolve("META-INF").resolve("MANIFEST.MF");
        if (Files.isRegularFile(manifestPath)) {
            try (InputStream in = Files.newInputStream(manifestPath)) {
                return new Manifest(in);
            }
        } else {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
            return manifest;
        }
    }

    private static List<Path> resolveMultiReleaseRoots(Path root, int targetVersion) {
        List<Path> paths = new ArrayList<>();
        for (int ver = targetVersion; ver > 8; ver --) {
            Path multiReleaseDir = root.resolve("META-INF").resolve("versions").resolve(Integer.toString(ver));
            if (Files.isDirectory(multiReleaseDir)) paths.add(multiReleaseDir);
        }
        return Collections.unmodifiableList(paths);
    }
    
    private static FileSystem buildJarFileSystem(Path initialRoot, List<Path> multiReleaseRoots) throws IOException {
        if (!multiReleaseRoots.isEmpty()) {
            List<Path> allPaths = Stream.concat(multiReleaseRoots.stream(), Stream.of(initialRoot)).toList();
            List<@Nullable PathMatcher> filters = Stream.concat(Stream.generate(() -> ExcludePrefix.EXCLUDE_META_INF).limit(multiReleaseRoots.size()), Stream.of(ExcludePrefix.EXCLUDE_MULTI_RELEASE)).toList();
            try {
                return FileSystems.newFileSystem(new URI("union::"), Map.of(
                        "paths", allPaths,
                        "filters", filters
                ));
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        } else if (initialRoot instanceof BasePath bp && Objects.equals(bp.getFileSystem().getRoot(), bp.toAbsolutePath())) {
            return bp.getFileSystem();
        } else {
            CompoundUriHelper.DeconstructedPath unionPath = new CompoundUriHelper.DeconstructedPath(List.of(initialRoot.toUri().toString()), "/");
            try {
                return Path.of(CompoundUriHelper.construct("union", unionPath)).getFileSystem();
            } catch (URISyntaxException e) {
                throw new IOException("Failed to construct union URI for jar root: " + unionPath, e);
            }
        }
    }

    private static Set<String> findPackages(FileSystem fs) throws IOException {
        Set<String> packages = new HashSet<>();
        Path root = fs.getPath("/");
        Files.walkFileTree(root, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName() != null && file.getFileName().toString().endsWith(".class") && attrs.isRegularFile() && file.getParent() != null) {
                    Path packageDir = root.relativize(file.getParent());
                    if (packageDir.getNameCount() > 0) {
                        String pkg = IntStream.range(0, packageDir.getNameCount()).mapToObj(packageDir::getName).map(Path::toString).collect(Collectors.joining("."));
                        if (NameHelper.validTypeName(pkg)) packages.add(pkg);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                return path.getNameCount() > 0 && Objects.equals("META-INF", path.getName(0).toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
        });

        return Set.copyOf(packages);
    }

    private static ModuleDescriptor createAutomaticModuleDescriptor(FileSystem fs, Manifest initialManifest, JarMetadataFilter filter, List<Path> paths) throws IOException {
        Optional<String> moduleName = Optional.ofNullable(initialManifest.getMainAttributes().getValue(NameHelper.AUTOMATIC_MODULE_NAME));
        moduleName = filter.filterAutomaticModuleName(moduleName, paths, fs);
        if (moduleName.isEmpty()) throw new IllegalStateException("Failed to infer automatic module name for jar: " + paths);

        ModuleDescriptor.Builder builder = ModuleDescriptor.newAutomaticModule(moduleName.get());

        Path serviceDir = fs.getPath("/", "META-INF", "services");
        if (Files.isDirectory(serviceDir)) {
            try (Stream<Path> servicePaths = Files.list(serviceDir)) {
                for (Path servicePath : servicePaths.toList()) {
                    if (Files.isRegularFile(servicePath) && servicePath.getFileName() != null) {
                        String serviceName = servicePath.getFileName().toString();
                        if (NameHelper.validTypeName(serviceName)) {
                            try (Stream<String> lines = Files.lines(servicePath, StandardCharsets.UTF_8)) {
                                List<String> implementations = lines.filter(NameHelper::validTypeName).toList();
                                builder.provides(serviceName, implementations);
                            }
                        }
                    }
                }
            }
        }

        return builder.build();
    }

    private static ModuleDescriptor extendModuleDescriptor(ModuleDescriptor initialModuleDescriptor, FileSystem fs, Manifest initialManifest) throws IOException {
        ModuleDescriptor.Builder builder = ModuleHelper.builder(initialModuleDescriptor);
        builder.packages(findPackages(fs));
        if (initialModuleDescriptor.version().isEmpty()) {
            String implVersion = initialManifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (implVersion != null) try {
                builder.version(ModuleDescriptor.Version.parse(implVersion));
            } catch (Exception e) {
                //
            }
        }
        if (initialModuleDescriptor.mainClass().isEmpty()) {
            String mainClass = initialManifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (NameHelper.validQualifiedClassName(mainClass)) {
                builder.mainClass(mainClass);
            }
        }
        return builder.build();
    }

    private static class ExcludePrefix implements PathMatcher {

        public static final PathMatcher EXCLUDE_META_INF = new ExcludePrefix("META-INF");
        public static final PathMatcher EXCLUDE_MULTI_RELEASE = new ExcludePrefix("META-INF", "versions");

        private final String prefix1;
        private final String[] prefix;
        private final Object lock;
        private final Map<FileSystem, Path> pathMap;

        private ExcludePrefix(String prefix1, String... prefix) {
            this.prefix1 = Objects.requireNonNull(prefix1);
            this.prefix = Arrays.stream(prefix).toArray(String[]::new);
            this.lock = new Object();
            this.pathMap = new WeakHashMap<>();
        }

        @Override
        public boolean matches(Path path) {
            synchronized (this.lock) {
                Path prefixPath = this.pathMap.computeIfAbsent(path.getFileSystem(), fs -> fs.getPath(this.prefix1, this.prefix));
                return !path.startsWith(prefixPath);
            }
        }
    }
}
