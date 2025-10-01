package bootstrap.jar.impl;

import bootstrap.jar.Jar;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.jar.Manifest;

@NotNullByDefault
public class JarImpl implements Jar {

    private final Manifest manifest;
    private final ModuleDescriptor descriptor;
    private final URI uri;
    private final FileSystem fs;
    private final JarModuleReference reference;

    JarImpl(Manifest manifest, ModuleDescriptor descriptor, FileSystem fs) {
        this.manifest = manifest;
        this.descriptor = descriptor;
        this.fs = fs;
        this.uri = fs.getPath(fs.getSeparator()).toUri();
        this.reference = new JarModuleReference(this.descriptor, this.manifest, this.fs);
    }

    FileSystem fileSystem() {
        return this.fs;
    }

    @Override
    public Manifest manifest() {
        return (Manifest) this.manifest.clone();
    }

    @Override
    public ModuleDescriptor descriptor() {
        return this.descriptor;
    }

    @Override
    public ModuleReference reference() {
        return this.reference;
    }

    @Override
    public URI uri() {
        return this.uri;
    }

    @Override
    public Path getPath(String first, String... more) {
        return this.fs.getPath(first, more);
    }
}
