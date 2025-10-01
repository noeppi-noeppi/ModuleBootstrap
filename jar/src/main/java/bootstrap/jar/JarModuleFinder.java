package bootstrap.jar;

import bootstrap.jar.impl.finder.JarModuleFinderImpl;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ModuleFinder} that can also locate {@link Jar jars} in addition to {@link ModuleReference module references}.
 */
@NotNullByDefault
public interface JarModuleFinder extends ModuleFinder {

    /**
     * Finds the jar providing the module with the given name, if any.
     */
    Optional<Jar> findJar(String name);

    /**
     * Returns a {@link ModuleFinder module finder} that finds modules from all given jars.
     */
    static JarModuleFinder of(Jar... jars) {
        return of(Arrays.asList(jars));
    }

    /**
     * Returns a {@link ModuleFinder module finder} that finds modules from all given jars.
     */
    static JarModuleFinder of(List<Jar> jars) {
        return new JarModuleFinderImpl(jars);
    }
}
