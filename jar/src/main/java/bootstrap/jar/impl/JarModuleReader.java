package bootstrap.jar.impl;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@NotNullByDefault
@SuppressWarnings("ClassCanBeRecord")
public class JarModuleReader implements ModuleReader {

    private final FileSystem fs;

    public JarModuleReader(FileSystem fs) {
        this.fs = fs;
    }

    @Override
    public Optional<URI> find(String name) throws IOException {
        Path path = this.fs.getPath(name);
        if (!Files.exists(path)) return Optional.empty();
        URI uri = path.toUri();
        try {
            return Optional.of(Files.isDirectory(path) ? new URI(uri + "/") : uri);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Optional<InputStream> open(String name) throws IOException {
        Path path = this.fs.getPath(name);
        if (!Files.isRegularFile(path)) return Optional.empty();
        return Optional.of(Files.newInputStream(path));
    }

    @Override
    public Stream<String> list() throws IOException {
        Path root = this.fs.getPath("/");
        //noinspection resource
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .map(path -> path.isAbsolute() ? root.relativize(path) : path)
                .map(Path::toString);
    }

    @Override
    public void close() {
        //
    }
}
