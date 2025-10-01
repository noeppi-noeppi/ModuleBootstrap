module bootstrap.spi {
    requires java.base;
    requires static org.jetbrains.annotations;

    exports bootstrap.api;
    exports bootstrap.spi;
}
