package bootstrap.jar.url.classtransformer;

import bootstrap.jar.classloading.ClassTransformer;
import bootstrap.jar.impl.classloading.LoaderPoolImpl;
import bootstrap.jar.impl.classloading.ModularClassLoader;
import bootstrap.jar.util.NameHelper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@NotNullByDefault
public class ClassTransformerStreamHandler extends URLStreamHandler {

    public static final String PROTOCOL = "classtransformer";
    public static final ClassTransformerStreamHandler INSTANCE = new ClassTransformerStreamHandler();

    private final ReadWriteLock lock;
    private final Map<String, LoaderPoolImpl> pools;

    private ClassTransformerStreamHandler() {
        this.lock = new ReentrantReadWriteLock();
        this.pools = new HashMap<>();
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        if (!Objects.equals(url.getProtocol(), PROTOCOL)) {
            throw new IllegalArgumentException("Wrong protocol: " + url.getProtocol());
        }
        if (url.getHost() == null || url.getPath() == null) {
            throw new FileNotFoundException(url.toString());
        }
        LoaderPoolImpl pool;
        this.lock.readLock().lock();
        try {
            pool = this.pools.get(url.getHost());
        } finally {
            this.lock.readLock().unlock();
        }
        if (pool == null) {
            throw new FileNotFoundException(url.toString());
        }
        String path = url.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.indexOf('/') < 0) {
            throw new FileNotFoundException(url.toString());
        }
        String moduleName = path.substring(0, path.indexOf('/'));
        String className = path.substring(path.indexOf('/') + 1);
        
        URLConnection con = null;
        if (NameHelper.validTypeName(moduleName)) {
            if (className.equals("META-INF.MANIFEST")) {
                con = this.resolveManifest(url, pool, moduleName);
            } else if (NameHelper.validTypeName(className)) {
                con = this.resolveTransformedClass(url, pool, moduleName, className);
            }
        }
        if (con != null) return con;
        throw new FileNotFoundException(url.toString());
    }
    
    private @Nullable URLConnection resolveManifest(URL url, LoaderPoolImpl pool, String moduleName) {
        ModularClassLoader loader = pool.getClassLoaderOrNull(moduleName);
        if (loader == null) return null;
        Optional<byte[]> data = loader.getManifestData(moduleName);
        return data.map(bytes -> new ClassFileUrlConnection(url, bytes, "application/java-manifest")).orElse(null);
    }
    
    private @Nullable URLConnection resolveTransformedClass(URL url, LoaderPoolImpl pool, String moduleName, String className) {
        try {
            byte[] data = pool.getTransformedClass(moduleName, className, ClassTransformer.REASON_RESOURCE, false);
            return new ClassFileUrlConnection(url, data, "application/java-vm");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static String registerPool(String requestedName, LoaderPoolImpl pool) {
        requestedName = requestedName.replaceAll("[^A-Za-z0-9._-]", "");
        INSTANCE.lock.writeLock().lock();
        try {
            if (!INSTANCE.pools.containsKey(requestedName)) {
                INSTANCE.pools.put(requestedName, pool);
                return requestedName;
            }
            int number = 0;
            while (INSTANCE.pools.containsKey(requestedName + "-" + number)) number += 1;
            INSTANCE.pools.put(requestedName + "-" + number, pool);
            return requestedName + "-" + number;
        } finally {
            INSTANCE.lock.writeLock().unlock();
        }
    }
}
