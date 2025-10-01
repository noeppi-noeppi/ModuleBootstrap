package bootstrap.jar.util;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Collections;
import java.util.Enumeration;

@NotNullByDefault
public class FlatteningEnumeration<T> implements Enumeration<T> {

    private final Enumeration<Enumeration<T>> elements;
    private Enumeration<T> current;

    public FlatteningEnumeration(Enumeration<Enumeration<T>> elements) {
        this.elements = elements;
        this.current = Collections.emptyEnumeration();
    }

    private void setupCurrent() {
        while (!this.current.hasMoreElements()) {
            if (!this.elements.hasMoreElements()) return;
            this.current = this.elements.nextElement();
        }
    }

    @Override
    public boolean hasMoreElements() {
        this.setupCurrent();
        return this.current.hasMoreElements();
    }

    @Override
    public T nextElement() {
        this.setupCurrent();
        return this.current.nextElement();
    }
}
