package bootstrap.jar.niofs.layered;

import bootstrap.jar.niofs.path.CompoundUriHelper;
import bootstrap.jar.niofs.path.DefaultFileSystem;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@NotNullByDefault
public class LayeredFileSystem extends DefaultFileSystem {

    private final List<String> layers;
    private final FileSystem fs;

    LayeredFileSystem(LayeredFileSystemProvider provider, List<String> layers) throws IOException {
        super(provider);
        if (layers.isEmpty()) throw new IOException("Empty layered filesystem.");

        try {
            while (true) {
                URI bottomURI = new URI(layers.getFirst());
                if (!Objects.equals(bottomURI.getScheme(), provider.getScheme())) break;
                CompoundUriHelper.DeconstructedPath bottomPath = CompoundUriHelper.deconstruct(provider.getScheme(), bottomURI);
                layers = Stream.concat(Stream.concat(bottomPath.components().stream(), Stream.of(bottomPath.path())), layers.stream().skip(1)).toList();
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid upstream filesystem: " + layers.getFirst());
        }

        this.layers = List.copyOf(layers);

        try {
            Path zipFile;
            if (this.layers.size() == 1) {
                zipFile = Paths.get(new URI(layers.getFirst()));
            } else {
                FileSystem upper = provider.getOrCreateFileSystem(this.layers.subList(0, this.layers.size() - 1), true, true);
                zipFile = upper.getPath(this.layers.getLast());
            }
            if (!Files.isRegularFile(zipFile)) {
                throw new NoSuchFileException("Archive in layered filesystem does not exist: " + zipFile.toUri());
            }
            this.fs = ZipFileSystemStore.openZipFileSystem(zipFile);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid upstream filesystem: " + this.layers.getFirst());
        }
    }

    public List<String> layers() {
        return this.layers;
    }

    public FileSystem upstreamFileSystem() {
        return this.fs;
    }
}
