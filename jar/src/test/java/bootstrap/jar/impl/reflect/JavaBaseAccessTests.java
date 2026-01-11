package bootstrap.jar.impl.reflect;

import bootstrap.jar.reflect.TrustedLookup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JavaBaseAccessTests {

    @Test
    void testJavaBaseAccess() {
        assertDoesNotThrow(TrustedLookup::get, "Failed to obtain trusted lookup");
        assertDoesNotThrow(JavaBaseAccess::get, "Failed to obtain JavaBaseAccess");
        // Once JavaBaseAccess has been constructed, all MethodHandles are in place and exist.
    }
}
