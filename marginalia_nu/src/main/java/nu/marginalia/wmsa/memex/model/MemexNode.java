package nu.marginalia.wmsa.memex.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

@AllArgsConstructor @Getter
public class MemexNode {
    private final MemexNodeUrl url;

    public MemexNodeType getType() {
        var fn = url.getFilename();
        if (fn.endsWith(".gmi")) {
            return MemexNodeType.DOCUMENT;
        }
        else if (fn.endsWith(".png")) {
            return MemexNodeType.IMAGE;
        }
        else if (fn.endsWith(".txt")) {
            return  MemexNodeType.TEXT;
        }
        else if (fn.contains(".")) {
            return MemexNodeType.OTHER;
        }
        return MemexNodeType.DIRECTORY;
    }

    @SneakyThrows
    public <T> T visit(MemexNodeVisitor<T> visitor) {
        return switch (getType()) {
            case DOCUMENT -> visitor.onDocument(url);
            case IMAGE -> visitor.onImage(url);
            default -> null;
        };
    }
    public interface MemexNodeVisitor<T> {
         T onDocument(MemexNodeUrl url) throws Exception;
         T onImage(MemexNodeUrl url) throws Exception;
    }
}
