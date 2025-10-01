package bootstrap.jar.impl.finder;

import bootstrap.jar.SplitModuleFinder;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.module.ModuleFinder;

@NotNullByDefault
public record SimpleSplitModuleFinder(ModuleFinder before, ModuleFinder after) implements SplitModuleFinder {}
