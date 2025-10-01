package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@NotNullByDefault
public class BasePath implements Path {

    private final BaseFileSystem fs;
    private final boolean absolute;
    private final boolean empty;
    private final String[] pathParts;

    private @Nullable BasePath normalized;

    BasePath(BaseFileSystem fs, String... pathParts) {
        this.fs = fs;
        if (pathParts.length == 0) {
            this.absolute = false;
            this.pathParts = new String[]{ "" };
            this.empty = true;
        } else {
            String pathString = Arrays.stream(pathParts)
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.joining(fs.getSeparator()));
            this.absolute = pathString.startsWith(fs.getSeparator());
            this.pathParts = splitPath(fs, this.absolute, pathString);
            this.empty = !this.absolute && this.pathParts.length == 1 && this.pathParts[0].isEmpty();
        }
        this.normalized = null;
    }

    private BasePath(BaseFileSystem fs, boolean absolute, String... pathParts) {
        this.fs = fs;
        this.absolute = absolute;
        this.pathParts = pathParts;
        this.empty = !this.absolute && this.pathParts.length == 1 && this.pathParts[0].isEmpty();
        this.normalized = null;
    }

    private static String[] splitPath(BaseFileSystem fs, boolean isAbsolute, String pathString) {
        String separatorPattern = "(?:" + Pattern.quote(fs.getSeparator()) + ")";
        String normalizedPathString = pathString
                .replace("/", fs.getSeparator()).replace("\\", fs.getSeparator())
                .replaceAll("^" + separatorPattern + "*|" + separatorPattern + "*$", "")
                .replaceAll(separatorPattern + "+(?=" + separatorPattern + ")", "");

        if (normalizedPathString.isEmpty()) {
            return isAbsolute ? new String[0] : new String[]{ "" };
        } else {
            return normalizedPathString.split(fs.getSeparator());
        }
    }

    @Override
    public BaseFileSystem getFileSystem() {
        return this.fs;
    }

    @Override
    public boolean isAbsolute() {
        return this.absolute;
    }

    @Override
    public @Nullable Path getRoot() {
        if (!this.absolute) return null;
        return this.fs.getRoot();
    }

    @Override
    public @Nullable Path getFileName() {
        if (this.empty) {
            return null;
        } else if (this.pathParts.length > 0) {
            return new BasePath(this.fs, false, this.pathParts[this.pathParts.length - 1]);
        } else {
            return this.absolute ? null : new BasePath(this.fs, false);
        }
    }

    @Override
    public @Nullable Path getParent() {
        if (this.pathParts.length > 1 || (this.absolute && this.pathParts.length == 1)) {
            return new BasePath(this.fs, this.absolute, Arrays.copyOf(this.pathParts,this.pathParts.length - 1));
        } else {
            return null;
        }
    }

    @Override
    public int getNameCount() {
        return this.pathParts.length;
    }

    @Override
    public Path getName(int index) {
        if (index < 0 || index > this.pathParts.length -1) throw new IllegalArgumentException();
        return new BasePath(this.fs, false, this.pathParts[index]);
    }

    @Override
    public BasePath subpath(int beginIndex, int endIndex) {
        if (!this.absolute && this.pathParts.length == 0 && beginIndex == 0 && endIndex == 1) {
            return new BasePath(this.fs, false);
        } else if (beginIndex < 0 || beginIndex > this.pathParts.length - 1 || endIndex < 0 || endIndex > this.pathParts.length || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Out of range " + beginIndex + " to " + endIndex + " for length " + this.pathParts.length);
        } else if (!this.absolute && beginIndex == 0 && endIndex == this.pathParts.length) {
            return this;
        } else {
            return new BasePath(this.fs, false, Arrays.copyOfRange(this.pathParts, beginIndex, endIndex));
        }
    }

    @Override
    public boolean startsWith(Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        } else if (other instanceof BasePath bp) {
            if (this.absolute != bp.absolute) return false;
            if (this.pathParts.length < bp.pathParts.length) return false;
            return checkArraysMatch(this.pathParts, bp.pathParts, false);
        } else {
            return false;
        }
    }


    @Override
    public boolean endsWith(Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        } else if (other instanceof BasePath bp) {
            if (!this.absolute && bp.absolute) return false;
            if (this.pathParts.length < bp.pathParts.length) return false;
            return checkArraysMatch(this.pathParts, bp.pathParts, true);
        } else {
            return false;
        }
    }

    private static boolean checkArraysMatch(String[] array1, String[] array2, boolean reverse) {
        int length = Math.min(array1.length, array2.length);
        IntBinaryOperator offset = reverse ? (l, i) -> l - i - 1 : (l, i) -> i;
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(array1[offset.applyAsInt(array1.length, i)], array2[offset.applyAsInt(array2.length, i)])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Path normalize() {
        if (this.normalized != null) return this.normalized;
        Deque<String> normalPathParts = new ArrayDeque<>();
        for (String pathPart : this.pathParts) {
            switch (pathPart) {
                case "." -> {}
                case ".." -> {
                    if (!this.absolute && (normalPathParts.isEmpty() || normalPathParts.getLast().equals(".."))) {
                        // .. on an empty path is allowed as long as it is not absolute, so keep it
                        normalPathParts.addLast(pathPart);
                    } else if (!normalPathParts.isEmpty()) {
                        normalPathParts.removeLast();
                    }
                }
                default -> normalPathParts.addLast(pathPart);
            }
        }
        this.normalized = new BasePath(this.fs, this.absolute, normalPathParts.toArray(String[]::new));
        this.normalized.normalized = this.normalized;
        return this.normalized;
    }

    @Override
    public Path resolve(Path other) {
        if (other instanceof BasePath bp) {
            if (bp.isAbsolute() || this.empty) return bp;
            if (bp.empty) return this;
            String[] mergedParts = new String[this.pathParts.length + bp.pathParts.length];
            System.arraycopy(this.pathParts, 0, mergedParts, 0, this.pathParts.length);
            System.arraycopy(bp.pathParts, 0, mergedParts, this.pathParts.length, bp.pathParts.length);
            return new BasePath(this.fs, this.absolute, mergedParts);
        }
        return other;
    }

    @Override
    public Path relativize(Path other) {
        if (other.getFileSystem()!=this.getFileSystem()) throw new IllegalArgumentException("Wrong filesystem");
        if (other instanceof BasePath bp) {
            if (this.absolute != bp.absolute) {
                throw new IllegalArgumentException("Different types of path");
            }
            int length = Math.min(this.pathParts.length, bp.pathParts.length);
            int i = 0;
            while (i < length) {
                if (!Objects.equals(this.pathParts[i], bp.pathParts[i])) break;
                i++;
            }

            int remaining = this.pathParts.length - i;
            if (remaining == 0 && i == bp.pathParts.length) {
                return new BasePath(this.getFileSystem(), false);
            } else if (remaining == 0) {
                return bp.subpath(i, bp.getNameCount());
            } else {
                String[] updots = IntStream.range(0, remaining).mapToObj(idx -> "..").toArray(String[]::new);
                if (i == bp.pathParts.length) {
                    return new BasePath(this.getFileSystem(), false, updots);
                } else {
                    BasePath subpath = bp.subpath(i, bp.getNameCount());
                    String[] mergedParts = new String[updots.length + subpath.pathParts.length];
                    System.arraycopy(updots, 0, mergedParts, 0, updots.length);
                    System.arraycopy(subpath.pathParts, 0, mergedParts, updots.length, subpath.pathParts.length);
                    return new BasePath(this.getFileSystem(), false, mergedParts);
                }
            }
        }
        throw new IllegalArgumentException("Wrong filesystem");
    }

    @Override
    public URI toUri() {
        try {
            return this.fs.provider().toURI(this);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path toAbsolutePath() {
        return this.absolute ? this : this.fs.getRoot().resolve(this);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return this.fs.toRealPath(this.toAbsolutePath()).normalize();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Path other) {
        if (other instanceof BasePath bp) {
            if (this.absolute && !bp.absolute) return 1;
            if (!this.absolute && bp.absolute) return -1;
            else return Arrays.compare(this.pathParts, bp.pathParts);
        } else {
            return 0;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof BasePath bp) {
            return this.fs == bp.fs && this.absolute == bp.absolute && Arrays.equals(this.pathParts, bp.pathParts);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.fs) + 31 * Arrays.hashCode(this.pathParts);
    }

    @Override
    public String toString() {
        return (this.absolute ? this.fs.getSeparator() : "") + String.join(this.fs.getSeparator(), this.pathParts);
    }
}
