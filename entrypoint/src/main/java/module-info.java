import bootstrap.entrypoint.Main;
import bootstrap.spi.Entrypoint;

module bootstrap.entrypoint {
    requires java.base;
    requires bootstrap.spi;
    requires static org.jetbrains.annotations;

    provides Entrypoint with Main;
}
