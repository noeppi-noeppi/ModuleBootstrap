package bootstrap.jar.classloading;

import org.jetbrains.annotations.NotNullByDefault;

/**
 * Provides a view of classes. Inside a {@link ClassDiscovery}, class names are unique. This could for example
 * be the view of classes visible to a specific {@link Module}.
 */
@NotNullByDefault
public interface ClassDiscovery {

    /**
     * Gets the transformed class data for a class.
     *
     * @param className The class name to resolve data for.
     * @param reason The reason why the transformation happens. This is passed to the {@link ClassTransformer}.
     * @throws ClassNotFoundException If the class is not found.
     */
    byte[] getTransformedClass(String className, String reason) throws ClassNotFoundException;
}
