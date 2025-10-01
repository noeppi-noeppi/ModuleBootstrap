package bootstrap.gradle.script;

import bootstrap.api.LauncherConstants;
import bootstrap.gradle.BootstrapExtension;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@NotNullByDefault
public class BootstrapScriptGenerator implements ScriptGenerator {
    
    private final boolean windows;
    private final BootstrapExtension bootstrapExt;
    private final Provider<FileCollection> bootstrapClasspath;

    public BootstrapScriptGenerator(boolean windows, BootstrapExtension bootstrapExt, Provider<FileCollection> bootstrapClasspath) {
        this.windows = windows;
        this.bootstrapExt = bootstrapExt;
        this.bootstrapClasspath = bootstrapClasspath;
    }

    @Override
    public void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        String template = this.loadTemplate();
        Map<String, String> replaceMap = this.buildReplaceMap();
        for (String key : replaceMap.keySet().stream().sorted().toList()) {
            template = template.replace(key, replaceMap.get(key));
        }
        try {
            destination.write(template);
        } catch (IOException e) {
            throw new RuntimeException("Filed to write start script", e);
        }
    }
    
    private String loadTemplate() {
        String templateName = this.windows ? "windowsStartScript.bat" : "unixStartScript.sh";
        String newline = this.windows ? "\r\n" : "\n";
        try (InputStream in = BootstrapScriptGenerator.class.getResourceAsStream(templateName)) {
            if (in == null) throw new FileNotFoundException(templateName);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().map(line -> line + newline).collect(Collectors.joining());
            }
        } catch (IOException e) {
            throw new RuntimeException("Filed to load start script template", e);
        }
    }
    
    private Map<String, String> buildReplaceMap() {
        return Map.of(
                "@@@MODULE_PATH@@@", this.buildFilePath("lib/boot", this.bootstrapExt.getBootModules().get()),
                "@@@MAIN_MODULE@@@", this.shellEscape(this.bootstrapExt.getMainModule().get()),
                "@@@JVM_ARGS@@@", this.getCombinedJvmArgs()
        );
    }
    
    private String getCombinedJvmArgs() {
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add(this.shellEscape("-D" + LauncherConstants.PROP_CLASSPATH + "=") + this.buildFilePath("lib/classpath", this.bootstrapClasspath.get()));
        jvmArgs.add(this.shellEscape("-D" + LauncherConstants.PROP_HOME + "=") + this.shellVariable("APP_HOME"));
        if (this.bootstrapExt.getEntrypoint().isPresent()) {
            jvmArgs.add(this.shellEscape("-D" + LauncherConstants.PROP_ENTRYPOINT + "=" + this.bootstrapExt.getEntrypoint().get()));
        }
        jvmArgs.addAll(this.bootstrapExt.getJvmArgs().get().stream().map(this::shellEscape).toList());
        return String.join(" ", jvmArgs);
    }
    
    private String buildFilePath(String basePath, FileCollection fc) {
        String sep;
        Function<String, String> nameMapper;
        if (this.windows) {
            sep = ";";
            nameMapper = name -> this.shellVariable("APP_HOME") + this.shellEscape("\\" + basePath.replace("/", "\\") + "\\" + name);
        } else {
            sep = ":";
            nameMapper = name -> this.shellVariable("APP_HOME") + this.shellEscape("/" + basePath + "/" + name);
        }
        Set<File> files = fc instanceof Configuration configuration ? configuration.resolve() : fc.getFiles();
        return files.stream().map(File::getName).sorted().map(nameMapper).collect(Collectors.joining(sep));
    }
    
    private String shellVariable(String var) {
        if (this.windows) {
            return "%" + var + "%";
        } else {
            return "\"${" + var + "}\"";
        }
    }
    
    private String shellEscape(String text) {
        if (this.windows) {
            StringBuilder sb = new StringBuilder();
            boolean needsQuotes = false;
            char[] chars = text.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                if (chars[i] == '"') {
                    sb.append((i >= 1 && chars[i - 1] == '\\') ? "\\\\^\"" : "\\^\"");
                    needsQuotes = true;
                } else if (chars[i] == '(' || chars[i] == ')' || chars[i] == '%' || chars[i] == '!' || chars[i] == '^'
                        || chars[i] == '<' || chars[i] == '>' || chars[i] == '&' || chars[i] == '|') {
                    sb.append('^').append(chars[i]);
                } else {
                    sb.append(chars[i]);
                }
            }
            return needsQuotes ? "\"" + sb + "\"" : sb.toString();
        } else {
            text = text.replace("\\", "\\\\");
            text = text.replace("\"", "\\\"");
            text = text.replace("'", "'\"'\"'");
            text = text.replace("`", "'\"`\"'");
            text = text.replace("$", "\\$");
            return "\"" + text + "\"";
        }
    }
}
