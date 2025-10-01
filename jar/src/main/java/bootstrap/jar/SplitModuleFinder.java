package bootstrap.jar;

import bootstrap.jar.impl.finder.DelegateSplitModuleFinder;
import bootstrap.jar.impl.finder.SimpleSplitModuleFinder;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A pair of module finders to be used as before and after parent finders when resolving a configuration.
 */
@NotNullByDefault
public interface SplitModuleFinder extends ModuleFinder {

    /**
     * Gets the {@link ModuleFinder} that shall be used before looking up parent module layers.
     */
    ModuleFinder before();
    
    /**
     * Gets the {@link ModuleFinder} that shall be used after looking up parent module layers.
     */
    ModuleFinder after();

    /**
     * Find a reference to a module by querying the {@link #before() before} finder. If the {@link #before() before}
     * finder locates the module, it is returned. Otherwise, the {@link #after() after} finder is queried.
     */
    @Override
    default Optional<ModuleReference> find(String name) {
        return this.before().find(name).or(() -> this.after().find(name));
    }

    /**
     * Finds all modules known to this finder as described in {@link #find(String)}.
     */
    @Override
    default Set<ModuleReference> findAll() {
        Set<ModuleReference> beforeModules = this.before().findAll();
        Set<ModuleReference> afterModules = this.after().findAll();
        Set<String> beforeModuleNames = beforeModules.stream()
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .collect(Collectors.toUnmodifiableSet());
        return Stream.concat(
                beforeModules.stream(),
                afterModules.stream().filter(ref -> !beforeModuleNames.contains(ref.descriptor().name()))
        ).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Invokes {@link #resolve(List, Collection)} where the set of root modules are all modules known to this finder.
     */
    default Configuration resolve(List<Configuration> parents) {
        Set<String> allModules = Stream.concat(this.before().findAll().stream(), this.after().findAll().stream())
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .collect(Collectors.toUnmodifiableSet());
        return this.resolve(parents, allModules);
    }

    /**
     * Invokes {@link Configuration#resolve(ModuleFinder, List, ModuleFinder, Collection)} with this pair
     * of module finders.
     */
    default Configuration resolve(List<Configuration> parents, Collection<String> rootModules) {
        return Configuration.resolve(this.before(), parents, this.after(), rootModules);
    }

    /**
     * Invokes {@link #resolveAndBind(List, Collection)} where the set of root modules are all modules known
     * to this finder.
     */
    default Configuration resolveAndBind(List<Configuration> parents) {
        Set<String> allModules = Stream.concat(this.before().findAll().stream(), this.after().findAll().stream())
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .collect(Collectors.toUnmodifiableSet());
        return this.resolveAndBind(parents, allModules);
    }
    
    /**
     * Invokes {@link Configuration#resolveAndBind(ModuleFinder, List, ModuleFinder, Collection)} with this
     * pair of module finders.
     */
    default Configuration resolveAndBind(List<Configuration> parents, Collection<String> rootModules) {
        return Configuration.resolveAndBind(this.before(), parents, this.after(), rootModules);
    }

    /**
     * Creates a new {@link SplitModuleFinder} using the provided {@link ModuleFinder module finders} as
     * {@link #before() before} and {@link #after() after} finders.
     */
    static SplitModuleFinder of(ModuleFinder before, ModuleFinder after) {
        return new SimpleSplitModuleFinder(before, after);
    }
    
    /**
     * Creates a new {@link SplitModuleFinder} that splits the modules from the provided
     * {@link ModuleFinder module finder} according to the provided {@link Predicate predicate}. If
     * {@code canReplaceModuleFromParentLayer} is {@code true} for a module name, it will be placed in the
     * {@link #before() before} finder, otherwise it will be placed in the {@link #after() after} finder.
     */
    static SplitModuleFinder of(ModuleFinder finder, Predicate<String> canReplaceModuleFromParentLayer) {
        return new DelegateSplitModuleFinder(finder, canReplaceModuleFromParentLayer);
    }
}
