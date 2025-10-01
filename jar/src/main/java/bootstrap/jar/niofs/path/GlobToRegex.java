package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.FileSystem;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@NotNullByDefault
public class GlobToRegex {

    public static String toRegexPattern(String glob, FileSystem fs) throws PatternSyntaxException {
        String dirSep = Pattern.quote(fs.getSeparator());
        String pathPartChar = fs.getSeparator().length() == 1 ? "[^" + dirSep + "]" : "(?:(?!" + dirSep + ").)";
        String pathPart = pathPartChar + "*";

        StringBuilder lit = new StringBuilder();
        StringBuilder pattern = new StringBuilder();
        int[] cp = glob.codePoints().toArray();
        boolean alternative = false;
        for (int i = 0; i < cp.length;) {
            if ("*?[{,}".indexOf(cp[i]) >= 0 && !lit.isEmpty()) {
                pattern.append(Pattern.quote(lit.toString()));
                lit = new StringBuilder();
            }
            switch (cp[i++]) {
                case '\\' -> {
                    if (i >= cp.length) throw new PatternSyntaxException("Truncated character escape", glob, i - 1);
                    lit.appendCodePoint(cp[i]);
                }
                case '/' -> lit.append(fs.getSeparator());
                case '*' -> {
                    if (i < cp.length && cp[i] == '*') {
                        i += 1;
                        pattern.append(".*");
                    } else {
                        pattern.append(pathPart);
                    }
                }
                case '?' -> pattern.append(pathPartChar);
                case '[' -> {
                    pattern.append("(?:(?!").append(dirSep).append(")[");
                    if (i >= cp.length) throw new PatternSyntaxException("Incomplete character class", glob, i - 1);
                    if (cp[i] == '!') {
                        i += 1;
                        pattern.append("^");
                    }
                    if (cp[i] == '-') {
                        i += 1;
                        pattern.append(Pattern.quote("-"));
                    }
                    while (i < cp.length && cp[i] != ']') {
                        if (cp[i] == '-') throw new PatternSyntaxException("Invalid character range", glob, i);
                        if (i + 2 < cp.length && cp[i + 1] == '-') {
                            if (cp[i + 2] == '-') throw new PatternSyntaxException("Invalid character range", glob, i + 2);
                            pattern.append(Pattern.quote(Character.toString(cp[i]))).append("-").append(Pattern.quote(Character.toString(cp[i + 2])));
                            i += 3;
                        } else {
                            pattern.append(Pattern.quote(Character.toString(cp[i])));
                            i += 1;
                        }
                    }
                    if (i >= cp.length) throw new PatternSyntaxException("Incomplete character class", glob, i - 1);
                    i += 1;
                    pattern.append("])");
                }
                case ',' -> pattern.append(alternative ? ")|(?:" : Pattern.quote(","));
                case '{' -> {
                    if (alternative) throw new PatternSyntaxException("Unsupported pattern group nesting", glob, i - 1);
                    pattern.append("(?:(?:");
                    alternative = true;
                }
                case '}' -> {
                    if (alternative) {
                        pattern.append("))");
                        alternative = false;
                    } else {
                        lit.append("}");
                    }
                }
                default -> lit.appendCodePoint(cp[i - 1]);
            }
        }
        if (!lit.isEmpty()) pattern.append(Pattern.quote(lit.toString()));
        return pattern.toString();
    }
}
