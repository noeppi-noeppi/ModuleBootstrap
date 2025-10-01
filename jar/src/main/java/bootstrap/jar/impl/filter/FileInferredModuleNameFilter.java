package bootstrap.jar.impl.filter;

import bootstrap.jar.JarMetadataFilter;
import bootstrap.jar.util.NameHelper;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NotNullByDefault
public class FileInferredModuleNameFilter implements JarMetadataFilter {

    public static final FileInferredModuleNameFilter INSTANCE = new FileInferredModuleNameFilter();
    private static final Pattern VERSION_PATTERN = Pattern.compile("-(\\d+(\\.|$))");

    private FileInferredModuleNameFilter() {}

    @Override
    public Optional<String> filterAutomaticModuleName(Optional<String> automaticModuleName, List<Path> paths, FileSystem fs) {
        if (automaticModuleName.isPresent()) return automaticModuleName;
        for (Path path : paths) {
            if (path.getFileName() != null) {
                String moduleName = moduleNameFromFileName(path.getFileName().toString());
                if (NameHelper.validTypeName(moduleName)) return Optional.of(moduleName);
            }
        }
        return Optional.empty();
    }

    private static String moduleNameFromFileName(String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) fileName = fileName.substring(0, fileName.length() - 4);
        Matcher m = VERSION_PATTERN.matcher(fileName);
        if (m.find()) fileName = fileName.substring(0, m.start());
        return fileName
                .replaceAll("[^A-Za-z0-9]", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("(^\\.)|(\\.$)", "");
    }
}
