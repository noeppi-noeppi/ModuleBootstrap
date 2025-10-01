package bootstrap.jar.util;

import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleDescriptor;
import java.util.Set;

@NotNullByDefault
public class ModuleHelper {

    public static final int COPY_PACKAGES   = 0b00000001;
    public static final int COPY_OPENS      = 0b00000010;
    public static final int COPY_EXPORTS    = 0b00000100;
    public static final int COPY_REQUIRES   = 0b00001000;
    public static final int COPY_USES       = 0b00010000;
    public static final int COPY_PROVIDES   = 0b00100000;
    public static final int COPY_MAIN_CLASS = 0b01000000;
    public static final int COPY_VERSION    = 0b10000000;
    public static final int COPY_ALL        = ~0;

    public static ModuleDescriptor.Builder builder(ModuleDescriptor descriptor) {
        return builder(descriptor.name(), descriptor.modifiers(), descriptor);
    }

    public static ModuleDescriptor.Builder builder(String moduleName, Set<ModuleDescriptor.Modifier> modifiers, ModuleDescriptor descriptor) {
        return builder(moduleName, modifiers, descriptor, COPY_ALL);
    }

    public static ModuleDescriptor.Builder builder(String moduleName, Set<ModuleDescriptor.Modifier> modifiers, ModuleDescriptor descriptor, int what) {
        Set<ModuleDescriptor.Modifier> cleanedModifiers = modifiers.contains(ModuleDescriptor.Modifier.AUTOMATIC) ? Set.of(ModuleDescriptor.Modifier.AUTOMATIC) : modifiers;
        ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(moduleName, cleanedModifiers);
        if ((what & COPY_PACKAGES) != 0) {
            builder.packages(descriptor.packages());
        }
        if (!modifiers.contains(ModuleDescriptor.Modifier.AUTOMATIC)) {
            if (!modifiers.contains(ModuleDescriptor.Modifier.OPEN) && (what & COPY_OPENS) != 0) {
                for (ModuleDescriptor.Opens opens : descriptor.opens()) builder.opens(opens);
            }
            if ((what & COPY_EXPORTS) != 0) {
                for (ModuleDescriptor.Exports exports : descriptor.exports()) builder.exports(exports);
            }
            if ((what & COPY_REQUIRES) != 0) {
                for (ModuleDescriptor.Requires requires : descriptor.requires()) builder.requires(requires);
            }
        }
        if ((what & COPY_USES) != 0) {
            for (String uses : descriptor.uses()) builder.uses(uses);
        }
        if ((what & COPY_PROVIDES) != 0) {
            for (ModuleDescriptor.Provides provides : descriptor.provides()) builder.provides(provides);
        }
        if ((what & COPY_MAIN_CLASS) != 0) {
            descriptor.mainClass().ifPresent(builder::mainClass);
        }
        if ((what & COPY_VERSION) != 0) {
            descriptor.version().ifPresent(builder::version);
        }
        return builder;
    }
}
