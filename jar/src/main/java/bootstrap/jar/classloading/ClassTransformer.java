package bootstrap.jar.classloading;

import bootstrap.jar.impl.classloading.ModularClassLoader;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/**
 * A transformer to transform raw class data as it is loaded in a {@link ModularClassLoader}.
 */
@NotNullByDefault
public interface ClassTransformer {

    /**
     * The reason used when a class transformation is requested due to classloading.
     *
     * @see #transformClass(TransformingEnvironment, String, String, byte[], String)
     */
    String REASON_CLASSLOADING = "classloading";

    /**
     * The reason used when a class transformation is requested due to resource loading.
     *
     * @see #transformClass(TransformingEnvironment, String, String, byte[], String)
     */
    String REASON_RESOURCE = "resource";

    /**
     * Transform the raw class data of a class.
     *
     * @param env Allows access back into the classloader.
     * @param moduleName The module in which this class is about to be loaded.
     * @param className The name of the class that is about to be transformed.
     * @param classData The raw class data.
     * @param reason The reason for the transformation request. If this request is due to classloading, this
     *               will be equal to {@link #REASON_CLASSLOADING}.
     * @return The transformed class data. This may be the same object as the provided class data. The transformer can
     *         return an empty array in which case the class is treated as not existing.
     */
    byte[] transformClass(TransformingEnvironment env, String moduleName, String className, byte[] classData, String reason);

    /**
     * Returns a {@link ClassTransformer} that does not transform any classes.
     */
    @SuppressWarnings("Convert2Lambda")
    static ClassTransformer noop() {
        return new ClassTransformer() {

            @Override
            public byte[] transformClass(TransformingEnvironment env, String className, @Nullable String moduleName, byte[] classData, String reason) {
                return classData;
            }
        };
    }
}
