import bootstrap.jar.niofs.empty.EmptyFileSystemProvider;
import bootstrap.jar.niofs.layered.LayeredFileSystemProvider;
import bootstrap.jar.niofs.union.UnionFileSystemProvider;
import bootstrap.jar.url.classtransformer.ClassTransformerStreamHandlerProvider;
import bootstrap.jar.url.path.PathStreamHandlerProvider;

import java.net.spi.URLStreamHandlerProvider;
import java.nio.file.spi.FileSystemProvider;

module bootstrap.jar {
    requires java.base;
    requires static org.jetbrains.annotations;

    exports bootstrap.jar;
    exports bootstrap.jar.classloading;
    exports bootstrap.jar.reflect to bootstrap.launcher;

    provides FileSystemProvider with EmptyFileSystemProvider, UnionFileSystemProvider, LayeredFileSystemProvider;
    provides URLStreamHandlerProvider with PathStreamHandlerProvider, ClassTransformerStreamHandlerProvider;
}
