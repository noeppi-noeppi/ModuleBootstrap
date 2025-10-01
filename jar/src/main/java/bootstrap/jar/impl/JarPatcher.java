package bootstrap.jar.impl;

import bootstrap.jar.Jar;
import bootstrap.jar.JarMetadataFilter;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

@NotNullByDefault
public class JarPatcher {

    public static Jar patch(Jar jar, JarMetadataFilter filter) throws IOException {
        FileSystem fs = getFileSystem(jar);
        ModuleDescriptor finalDescriptor = filter.filterModuleDescriptor(jar.descriptor(), fs);

        Manifest initialManifest = (Manifest) jar.manifest().clone();
        JarFactory.setManifestAttributesFromDescriptor(initialManifest, finalDescriptor);
        Manifest finalManifest = (Manifest) filter.filterManifest(initialManifest, fs, finalDescriptor).clone();
        return new JarImpl(finalManifest, finalDescriptor, fs);
    }

    private static FileSystem getFileSystem(Jar jar) throws IOException {
        if (jar instanceof JarImpl impl) {
            return impl.fileSystem();
        } else try {
            return FileSystems.newFileSystem(new URI("union::"), Map.of(
                    "paths", List.of(jar.getPath("/"))
            ));
        } catch (URISyntaxException e) {
            throw new IOException("Failed to construct union URI for jar root.", e);
        }
    }
}
