package bootstrap.jar.niofs.union;

import bootstrap.jar.niofs.path.BasePath;
import bootstrap.jar.niofs.path.CompoundUriHelper;
import bootstrap.jar.niofs.path.DefaultFileSystem;
import bootstrap.jar.niofs.path.ReadOnlyFileSystemProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

@NotNullByDefault
public class UnionFileSystemProvider extends ReadOnlyFileSystemProvider {

    private final Object lock;
    private final Set<UUID> usedIds;
    private final Map<List<String>, UnionFileSystem> fileSystems;

    public UnionFileSystemProvider() {
        this.lock = new Object();
        this.usedIds = new HashSet<>();
        this.fileSystems = new HashMap<>();
    }

    @Override
    public String getScheme() {
        return "union";
    }

    @Override
    public Path getPath(URI uri) {
        CompoundUriHelper.DeconstructedPath dec = CompoundUriHelper.deconstruct(this.getScheme(), uri);
        try {
            boolean isAnonymous = !dec.components().isEmpty() && dec.components().getFirst().isEmpty();
            return this.getOrCreateFileSystem(dec.components(), true, !isAnonymous, null).getPath(dec.path());
        } catch (IOException e) {
            throw this.mask(dec.components(), e);
        }
    }

    @Override
    public URI toURI(BasePath path) throws URISyntaxException {
        if (path.getFileSystem() instanceof UnionFileSystem ufs) {
            return CompoundUriHelper.construct(this.getScheme(), new CompoundUriHelper.DeconstructedPath(ufs.key(), path.toString()));
        } else {
            throw new IllegalStateException("Wrong kind of path.");
        }
    }

    @Override
    public FileSystem newFileSystem(URI uri, @Nullable Map<String, ?> env) throws IOException {
        CompoundUriHelper.DeconstructedPath dec = CompoundUriHelper.deconstruct(this.getScheme(), uri);
        return this.getOrCreateFileSystem(dec.components(), false, true, env);
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        CompoundUriHelper.DeconstructedPath dec = CompoundUriHelper.deconstruct(this.getScheme(), uri);
        try {
            return this.getOrCreateFileSystem(dec.components(), true, false, null);
        } catch (IOException e) {
            throw this.mask(dec.components(), e);
        }
    }

    @Override
    public void unregisterFileSystem(FileSystem fs) {
        synchronized (this.lock) {
            if (fs instanceof UnionFileSystem ufs) {
                //noinspection resource
                this.fileSystems.remove(ufs.key());
            }
        }
    }

    private UnionFileSystem getOrCreateFileSystem(List<String> roots, boolean existing, boolean create, @Nullable Map<String, ?> env) throws IOException {
        roots = List.copyOf(roots);
        UUID uid;
        synchronized (this.lock) {
            UnionFileSystem existingFileSystem = this.fileSystems.get(roots);
            if (existingFileSystem != null) {
                if (existing) return existingFileSystem;
                throw new FileSystemAlreadyExistsException(this.fsError(roots));
            }
            if (!create) {
                throw new FileSystemNotFoundException(this.fsError(roots));
            }
            do uid = UUID.randomUUID(); while (this.usedIds.contains(uid));
            this.usedIds.add(uid);
        }
        // Release the lock when creating file systems to prevent deadlocks while creating the nested file systems
        UnionFileSystem newFS;
        try {
            if (!existing && env != null && env.containsKey("paths")) {
                if (!(env.get("paths") instanceof List<?> pathList)) throw new IOException("The 'paths' environment must contain a list.");
                List<?> filterList = null;
                if (env.containsKey("filters")) {
                    if (!(env.get("filters") instanceof List<?> f)) throw new IOException("The 'filters' environment must contain a list.");
                    filterList = f;
                }
                newFS = new UnionFileSystem(this, uid, pathList, filterList);
            } else {
                newFS = new UnionFileSystem(this, roots);
                synchronized (this.lock) { this.usedIds.remove(uid); }
            }
        } catch (IOException e) {
            throw new IOException(this.fsError(roots), e);
        }
        synchronized (this.lock) {
            // If another filesystem with the same key was created while the lock was released, discard the previous file system.
            UnionFileSystem existingFileSystem = this.fileSystems.get(newFS.key());
            if (existingFileSystem != null) {
                // Do not close the new filesystem as it has not yet been added to the filesystem list.
                return existingFileSystem;
            }
            this.fileSystems.put(newFS.key(), newFS);
            return newFS;
        }
    }

    private String fsError(List<String> roots) {
        return this.getScheme() + ":" + roots.stream().map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8)).collect(Collectors.joining(":"));
    }

    private FileSystemNotFoundException mask(List<String> roots, IOException e) {
        FileSystemNotFoundException ex = new FileSystemNotFoundException(this.fsError(roots));
        ex.initCause(e);
        throw ex;
    }

    private Path resolveUpstream(Path path) throws NoSuchFileException {
        if (path.getFileSystem() instanceof UnionFileSystem ufs) {
            for (UnionFileSystem.UnionRoot root : ufs.roots()) {
                if (!root.testLocalPath(path)) continue;
                Path upstream = root.resolveUpstreamPath(path);
                if (Files.exists(upstream)) return upstream;
            }
        }
        throw new NoSuchFileException(path.toString());
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        for (OpenOption option : options) {
            if (option != StandardOpenOption.READ) throw new UnsupportedOperationException("Unsupported OpenOption: " + option);
        }
        return Files.newByteChannel(this.resolveUpstream(path), options);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (!(dir.getFileSystem() instanceof UnionFileSystem ufs)) return new DefaultFileSystem.SimpleDirectoryStream(Collections.emptyList(), filter);
        Set<Path> localPaths = new TreeSet<>();
        boolean hasAnyDirectory = false;
        for (UnionFileSystem.UnionRoot root : ufs.roots()) {
            Path upstream = root.resolveUpstreamPath(dir);
            if (!Files.isDirectory(upstream)) continue;
            hasAnyDirectory = true;

            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(upstream, filter)) {
                StreamSupport.stream(dirStream.spliterator(), false)
                        .map(upstream::relativize)
                        .map(rel -> dir.resolve(dir.getFileSystem().getPath(
                                IntStream.range(0, rel.getNameCount())
                                        .mapToObj(rel::getName)
                                        .map(Path::toString)
                                        .collect(Collectors.joining(dir.getFileSystem().getSeparator()))
                        )))
                        .filter(root::testLocalPath)
                        .forEach(localPaths::add);
            }
        }

        if (!hasAnyDirectory) throw new NotDirectoryException(dir.toString());
        return new DefaultFileSystem.SimpleDirectoryStream(localPaths, filter);
    }

    @Override
    public boolean exists(Path path, LinkOption... options) {
        try {
            return Files.exists(this.resolveUpstream(path));
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        for (AccessMode mode : modes) {
            if (mode == AccessMode.WRITE || mode == AccessMode.EXECUTE) {
                throw new AccessDeniedException(path.toString());
            }
        }
        Path upstream = this.resolveUpstream(path);
        upstream.getFileSystem().provider().checkAccess(upstream, modes);
    }

    @Override
    public boolean isSameFile(Path path1, Path path2) throws IOException {
        if (Objects.equals(path1, path2)) return true;
        try {
            Path upstream1 = this.resolveUpstream(path1);
            Path upstream2 = this.resolveUpstream(path2);
            if (upstream1.getFileSystem().provider() != upstream2.getFileSystem().provider()) {
                return false;
            }
            return Files.isSameFile(upstream1, upstream2);
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class) {
            Path upstream = this.resolveUpstream(path);
            return Files.readAttributes(upstream, type);
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
