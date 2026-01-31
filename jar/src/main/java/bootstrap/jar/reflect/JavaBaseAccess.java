package bootstrap.jar.reflect;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

@NotNullByDefault
public class JavaBaseAccess {

    private static @Nullable JavaBaseAccess instance;

    public static JavaBaseAccess get() {
        try {
            if (instance == null) instance = new JavaBaseAccess(TrustedLookup.get());
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private final MethodHandle bindLayerToLoader;
    private final MethodHandle assignPackageToModule;
    private final MethodHandle getThreadInterruptLock;
    private final MethodHandle enableNativeAccess;

    public JavaBaseAccess(MethodHandles.Lookup trustedLookup) throws ReflectiveOperationException {
        this.bindLayerToLoader = trustedLookup.findVirtual(ModuleLayer.class, "bindToLoader", MethodType.methodType(void.class, ClassLoader.class));
        VarHandle namedPackageModule = trustedLookup.findVarHandle(trustedLookup.findClass("java.lang.NamedPackage"), "module", Module.class);
        this.assignPackageToModule = namedPackageModule.toMethodHandle(VarHandle.AccessMode.SET_VOLATILE);
        VarHandle threadInterruptLock = trustedLookup.findVarHandle(Thread.class, "interruptLock", Object.class);
        this.getThreadInterruptLock = threadInterruptLock.toMethodHandle(VarHandle.AccessMode.GET);
        this.enableNativeAccess = trustedLookup.findVirtual(Module.class, "implAddEnableNativeAccess", MethodType.methodType(Module.class));
    }

    public void bindLayerToLoader(ModuleLayer layer, ClassLoader loader) {
        try {
            this.bindLayerToLoader.invoke(layer, loader);
        } catch (Throwable e) {
            throw throwUnchecked(e);
        }
    }

    public void assignPackageToModule(Package pkg, Module module) {
        try {
            this.assignPackageToModule.invoke(pkg, module);
        } catch (Throwable e) {
            throw throwUnchecked(e);
        }
    }

    public Object getThreadInterruptLock(Thread thread) {
        try {
            return this.getThreadInterruptLock.invoke(thread);
        } catch (Throwable e) {
            throw throwUnchecked(e);
        }
    }

    public void enableNativeAccess(Module module) {
        try {
            this.enableNativeAccess.invoke(module);
        } catch (Throwable e) {
            throw throwUnchecked(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> Error throwUnchecked(Throwable t) throws T {
        throw (T) t;
    }
}
