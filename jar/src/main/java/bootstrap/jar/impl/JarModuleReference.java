package bootstrap.jar.impl;

import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.file.FileSystem;
import java.util.jar.Manifest;

@NotNullByDefault
public class JarModuleReference extends ModuleReference {

    private final Manifest manifest;
    private final FileSystem fs;

    public JarModuleReference(ModuleDescriptor descriptor, Manifest manifest, FileSystem fs) {
        super(descriptor, fs.getPath(fs.getSeparator()).toUri());
        this.manifest = manifest;
        this.fs = fs;
    }

    public Manifest manifest() {
        return (Manifest) this.manifest.clone();
    }

    @Override
    public ModuleReader open() {
        return new JarModuleReader(this.fs);
    }
}
