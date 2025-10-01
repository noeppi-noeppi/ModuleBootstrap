package bootstrap.jar.reflect;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Provides a way to access the trusted lookup.
 */
@NotNullByDefault
public class TrustedLookup {

    private static volatile @Nullable MethodHandles.Lookup instance;

    /**
     * Retrieves the trusted lookup.
     */
    public static MethodHandles.Lookup get() {
        if (instance != null) return Objects.requireNonNull(instance);
        synchronized (TrustedLookup.class) {
            if (instance != null) return Objects.requireNonNull(instance);
            checkJavaBaseOpen();
            try {
                instance = retrieveLookup();
            } catch (Exception | IllegalAccessError e) {
                throw new RuntimeException("Failed to retrieve the trusted lookup.", e);
            }
            return Objects.requireNonNull(instance);
        }
    }

    private static void checkJavaBaseOpen() {
        Module javaBase = Object.class.getModule();
        Module self = TrustedLookup.class.getModule();
        if (!javaBase.isOpen("java.lang.invoke", self)) {
            String selfName = Objects.requireNonNullElse(self.getName(), "ALL-UNNAMED");
            throw new Error("Invalid module path configuration. Update your java arguments to include --add-opens java.base/java.lang.invoke=" + selfName);
        }
    }

    private static MethodHandles.Lookup retrieveLookup() throws ReflectiveOperationException {
        Field fd = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        fd.setAccessible(true);
        return Objects.requireNonNull((MethodHandles.Lookup) fd.get(null), "IMPL_LOOKUP is null");
    }
}
