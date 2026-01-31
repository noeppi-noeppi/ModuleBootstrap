package bootstrap.jar.impl.finder;

import bootstrap.jar.SplitModuleFinder;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@NotNullByDefault
public class DelegateSplitModuleFinder implements SplitModuleFinder {
    
    private final ModuleFinder parent;
    private final Predicate<String> canReplaceModuleFromParentLayer;
    private final ModuleFinder before;
    private final ModuleFinder after;

    public DelegateSplitModuleFinder(ModuleFinder parent, Predicate<String> canReplaceModuleFromParentLayer) {
        this.parent = parent;
        this.canReplaceModuleFromParentLayer = new MemoizedPredicate<>(canReplaceModuleFromParentLayer);
        this.before = new FilteredModuleFinder(true);
        this.after = new FilteredModuleFinder(false);
    }

    @Override
    public ModuleFinder before() {
        return this.before;
    }

    @Override
    public ModuleFinder after() {
        return this.after;
    }

    @Override
    public Optional<ModuleReference> find(String name) {
        return this.parent.find(name);
    }

    @Override
    public Set<ModuleReference> findAll() {
        return this.parent.findAll();
    }

    private static class MemoizedPredicate<T> implements Predicate<T> {
        
        private final Predicate<T> predicate;
        private final Map<T, Boolean> values;

        private MemoizedPredicate(Predicate<T> predicate) {
            this.predicate = predicate;
            this.values = new HashMap<>();
        }

        @Override
        public synchronized boolean test(T key) {
            return this.values.computeIfAbsent(key, this.predicate::test);
        }
    }
    
    private class FilteredModuleFinder implements ModuleFinder {

        private final boolean isBefore;

        private FilteredModuleFinder(boolean isBefore) {
            this.isBefore = isBefore;
        }

        @Override
        public Optional<ModuleReference> find(String name) {
            if (DelegateSplitModuleFinder.this.canReplaceModuleFromParentLayer.test(name) == this.isBefore) {
                return DelegateSplitModuleFinder.this.parent.find(name);
            } else {
                return Optional.empty();
            }
        }

        @Override
        public Set<ModuleReference> findAll() {
            return DelegateSplitModuleFinder.this.parent.findAll().stream()
                    .filter(ref -> DelegateSplitModuleFinder.this.canReplaceModuleFromParentLayer.test(ref.descriptor().name()) == this.isBefore)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
