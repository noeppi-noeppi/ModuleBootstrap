package bootstrap.jar.niofs.union;

import bootstrap.jar.niofs.path.DefaultFileSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@NotNullByDefault
public class UnionFileSystem extends DefaultFileSystem {

    private final List<String> key;
    private final List<UnionRoot> roots;

    UnionFileSystem(UnionFileSystemProvider provider, List<String> key) throws IOException {
        super(provider);
        this.key = List.copyOf(key);
        if (this.key.isEmpty()) throw new IOException("Empty union filesystem.");
        List<UnionRoot> roots = new ArrayList<>(this.key.size());
        for (String part : this.key) {
            if (part.isEmpty()) throw new IOException("Empty filesystem key.");
            try {
                roots.add(new UnionRoot(Path.of(new URI(part)), p -> true));
            } catch (URISyntaxException | IllegalArgumentException e) {
                throw new IOException("Invalid path root: " + part, e);
            }
        }
        this.roots = List.copyOf(roots);
    }

    UnionFileSystem(UnionFileSystemProvider provider, UUID uid, List<?> rootPaths, @Nullable List<?> filters) throws IOException {
        super(provider);
        this.key = List.of("", uid.toString());
        if (rootPaths.isEmpty()) throw new IOException("Empty union filesystem.");
        if (filters == null) filters = IntStream.range(0, rootPaths.size()).mapToObj(i -> null).toList();
        if (rootPaths.size() != filters.size()) throw new IOException("Wrong amount of filters.");
        List<UnionRoot> roots = new ArrayList<>(rootPaths.size());
        for (int i = 0; i < rootPaths.size(); i++) {
            Path path = switch (rootPaths.get(i)) {
                case Path p -> p;
                case FileSystem f -> f.getPath("");
                case URI u -> Path.of(u);
                case String s -> { try { yield Path.of(new URI(s)); } catch (URISyntaxException e) { throw new IOException("Invalid path root: " + s); } }
                case null, default -> throw new IOException("Invalid path root: " + rootPaths.get(i));
            };
            PathMatcher filter = switch (filters.get(i)) {
                case null -> p -> true;
                case PathMatcher m -> m;
                case String s -> this.getPathMatcher(s.contains(":") ? s : "glob:" + s);
                default -> throw new IOException("Invalid path filter: " + filters.get(i));
            };
            roots.add(new UnionRoot(path, filter));
        }
        this.roots = List.copyOf(roots);
    }

    public List<String> key() {
        return this.key;
    }

    public List<UnionRoot> roots() {
        return this.roots;
    }

    public record UnionRoot(Path path, PathMatcher filter) {

        public boolean testLocalPath(Path path) {
            if (path.isAbsolute()) path = path.getRoot().relativize(path);
            path = path.normalize();
            return this.filter.matches(path);
        }

        public Path resolveUpstreamPath(Path path) {
            if (path.isAbsolute()) path = path.getRoot().relativize(path);
            path = path.normalize();
            Path upstream = this.path();
            for (int i = 0; i < path.getNameCount(); i++) {
                upstream = upstream.resolve(path.getName(i).toString());
            }
            return upstream;
        }
    }
}
