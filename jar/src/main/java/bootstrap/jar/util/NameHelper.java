package bootstrap.jar.util;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.jar.Attributes;

@NotNullByDefault
public class NameHelper {

    public static final Attributes.Name AUTOMATIC_MODULE_NAME = new Attributes.Name("Automatic-Module-Name");

    private static final Set<String> RESERVED_IDENT = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_"
    );

    public static boolean validJavaIdentifier(@Nullable String ident) {
        if (ident == null || ident.isEmpty() || RESERVED_IDENT.contains(ident)) return false;
        if (!Character.isJavaIdentifierStart(Character.codePointAt(ident, 0))) return false;
        return ident.codePoints().skip(1).allMatch(Character::isJavaIdentifierPart);
    }

    public static boolean validTypeName(@Nullable String name) {
        if (name == null || name.isEmpty()) return false;
        return Arrays.stream(name.split("\\.", -1)).allMatch(NameHelper::validJavaIdentifier);
    }

    public static boolean validQualifiedClassName(@Nullable String name) {
        return validTypeName(name) && name.indexOf('.') >= 0;
    }

    public static boolean validLoadableClassName(@Nullable String name) {
        if (name == null || name.isEmpty()) return false;
        int idx = name.lastIndexOf('.');
        String packageName = idx < 0 ? null : name.substring(0, idx);
        String simpleName = idx < 0 ? name : name.substring(idx + 1);
        return (packageName == null || validTypeName(packageName)) && (validJavaIdentifier(simpleName) || "package-info".equals(simpleName));
    }
}
