package nu.marginalia.converting.sideload.warc;

import nu.marginalia.converting.sideload.SideloadSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WarcSideloadFactory {

    public Collection<? extends SideloadSource> createSideloaders(Path pathToWarcFiles) throws IOException {
        final List<Path> files = new ArrayList<>();

        try (var stream = Files.list(pathToWarcFiles)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(this::isWarcFile)
                    .forEach(files::add);

        }
        // stub
        return null;
    }

    private boolean isWarcFile(Path path) {
        return path.toString().endsWith(".warc")
            || path.toString().endsWith(".warc.gz");
    }
}