package bootstrap.jar.niofs.path;

import org.jetbrains.annotations.NotNullByDefault;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@NotNullByDefault
public class CompoundUriHelper {

    public static URI construct(String scheme, DeconstructedPath dec) throws URISyntaxException {
        String components = dec.components().stream().map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("%2F", "/")).collect(Collectors.joining(":"));
        String path = URLEncoder.encode(dec.path(), StandardCharsets.UTF_8).replace("%2F", "/");
        return new URI(scheme + ":" + components + (path.isEmpty() ? "" : "!" + path));
    }

    public static DeconstructedPath deconstruct(String scheme, URI uri) {
        if (!Objects.equals(scheme, uri.getScheme())) throw new IllegalArgumentException("Wrong URI scheme, expected " + scheme + ", got " + uri.getScheme());
        String ssp = uri.getRawSchemeSpecificPart();
        if (ssp == null) throw new IllegalArgumentException("Empty URI roots.");
        String rootsString = ssp.indexOf('!') >= 0 ? ssp.substring(0, ssp.indexOf('!')) : ssp;
        String pathString = ssp.indexOf('!') >= 0 ? ssp.substring(ssp.indexOf('!') + 1) : "";
        List<String> roots = Arrays.stream(rootsString.split(":")).map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8)).toList();
        String path = URLDecoder.decode(pathString, StandardCharsets.UTF_8);
        return new DeconstructedPath(roots, path);
    }

    public record DeconstructedPath(List<String> components, String path) {

        @Override
        public String toString() {
            return this.components().stream().map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8)).collect(Collectors.joining(":")) + "!" + URLEncoder.encode(this.path(), StandardCharsets.UTF_8);
        }
    }
}
