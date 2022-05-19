package nu.marginalia.wmsa.memex.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

@Getter @EqualsAndHashCode
public class MemexNodeUrl implements MemexUrl, Comparable<MemexNodeUrl> {
    private final String url;

    public MemexNodeUrl(String url) {
        if (url.startsWith("//")) {
            this.url = url.substring(1);
        } else {
            this.url = url;
        }
    }
    public static MemexNodeUrl ofRelativePath(Path root, Path relative) {
        Path path;

        if (relative.startsWith("/")) {
            path = root.relativize(relative);
        }
        else {
            path = relative;
        }

        if (File.separatorChar == '\\')
            return new MemexNodeUrl("/" + path.toString().replace('\\', '/'));
        return new MemexNodeUrl("/" + path);
    }

    public String toString() {
        return url;
    }

    public String getParentStr() {
        var path = asRelativePath().getParent();
        if (path == null) {
            return null;
        }
        return path.toString();
    }
    public MemexNodeUrl getParentUrl() {
        var str = getParentStr();
        if (str == null) {
            return null;
        }
        return new MemexNodeUrl(str);
    }
    public MemexNodeUrl sibling(String name) {
        return new MemexNodeUrl(asRelativePath().resolveSibling(name).toString());
    }
    public MemexNodeUrl child(String name) {
        return new MemexNodeUrl(asRelativePath().resolve(name).toString());
    }

    public Path asRelativePath() {
        return Path.of(url);
    }

    public Path asAbsolutePath(Path root) {
        Path p =  Path.of(root + url);
        if (p.toString().contains(".git")) {
            throw new IllegalStateException(url + " touched .git");
        }
        if (!p.normalize().startsWith(root)) {
            throw new IllegalStateException(url + " escaped Memex root as " + p);
        }
        return p;
    }


    public String getFilename() { return asRelativePath().toFile().getName(); }

    @Override
    public void visitNodeUrl(Consumer<MemexNodeUrl> fn) {
        fn.accept(this);
    }

    @Override
    public Optional<MemexNodeUrl> getNodeUrl() {
        return Optional.of(this);
    }
    @Override
    public int compareTo(@NotNull MemexNodeUrl o) {
        return url.compareTo(o.getUrl());
    }

    public MemexNode toNode() {
        return new MemexNode(this);
    }
}
