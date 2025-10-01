package bootstrap.jar.impl.classloading;

import org.jetbrains.annotations.NotNullByDefault;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@NotNullByDefault
public class RuntimeClassMap {

    private final Map<Key, URL> map;
    private final ReadWriteLock lock;

    public RuntimeClassMap() {
        this.map = new HashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void addRuntimeClass(String moduleName, String className, URL resource) {
        Key key = new Key(moduleName, className);
        this.lock.writeLock().lock();
        try {
            if (!this.map.containsKey(key)) {
                this.map.put(key, resource);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public Optional<URL> getRuntimeClass(String moduleName, String className) {
        Key key = new Key(moduleName, className);
        this.lock.readLock().lock();
        try {
            return Optional.ofNullable(this.map.get(key));
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private record Key(String moduleName, String className) {}
}
