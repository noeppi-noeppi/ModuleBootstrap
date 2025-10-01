package bootstrap.entrypoint;

import bootstrap.spi.Entrypoint;
import bootstrap.api.ModuleSystem;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * Provides a sample entrypoint that prints {@literal Hello, world!}
 */
@NotNullByDefault
public class Main implements Entrypoint {

    @Override
    public String name() {
        return "example";
    }

    @Override
    public void main(ModuleSystem system, String[] args) {
        System.out.println("Hello, world!");
    }
}
