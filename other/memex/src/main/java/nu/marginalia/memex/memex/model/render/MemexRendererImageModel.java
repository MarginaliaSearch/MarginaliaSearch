package nu.marginalia.memex.memex.model.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import nu.marginalia.memex.memex.model.MemexLink;
import nu.marginalia.memex.memex.model.MemexImage;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

@AllArgsConstructor @Getter
public class MemexRendererImageModel {
    public final MemexImage image;
    public final List<MemexLink> backlinks;

    public final String parent;

    public String getParent() {
        if ("/".equals(parent) || parent.isBlank()) {
            return null;
        }
        return parent;
    }

    public MemexNodeUrl getPath() {
        return image.path;
    }

    @SneakyThrows
    public String getData() {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(image.realPath));
    }
}