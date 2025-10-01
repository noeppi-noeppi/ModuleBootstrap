package bootstrap.launcher;

import bootstrap.api.ModuleSystem;
import bootstrap.jar.reflect.TrustedLookup;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.invoke.MethodHandles;

@NotNullByDefault
public class ModuleSystemImpl implements ModuleSystem {

    private final MethodHandles.Lookup trustedLookup;
    private final ModuleLayer bootLayer;
    private final ModuleLayer.Controller bootstrapController;

    public ModuleSystemImpl(ModuleLayer bootLayer, ModuleLayer.Controller bootstrapController) {
        this.trustedLookup = TrustedLookup.get();
        this.bootLayer = bootLayer;
        this.bootstrapController = bootstrapController;
    }

    @Override
    public MethodHandles.Lookup trustedLookup() {
        return this.trustedLookup;
    }

    @Override
    public ModuleLayer bootLayer() {
        return this.bootLayer;
    }

    @Override
    public ModuleLayer layer() {
        return this.bootstrapController.layer();
    }

    @Override
    public void addOpens(Module source, String pkg, Module target) {
        this.bootstrapController.addOpens(source, pkg, target);
    }

    @Override
    public void addExports(Module source, String pkg, Module target) {
        this.bootstrapController.addExports(source, pkg, target);
    }

    // bootstrapController.addReads is purposely not exposed as it won't work with the classloader architecture anyway.
}
