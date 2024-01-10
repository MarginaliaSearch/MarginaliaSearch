package nu.marginalia.converting.sideload.warc;

import com.google.inject.Inject;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloaderProcessing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WarcSideloadFactory {

    private final SideloaderProcessing processing;

    @Inject
    public WarcSideloadFactory(SideloaderProcessing processing) {
        this.processing = processing;
    }

    public Collection<? extends SideloadSource> createSideloaders(Path pathToWarcFiles) throws IOException {
        final List<Path> files = new ArrayList<>();

        try (var stream = Files.list(pathToWarcFiles)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(this::isWarcFile)
                    .forEach(files::add);

        }

        List<WarcSideloader> sources = new ArrayList<>();

        for (Path file : files) {
            sources.add(new WarcSideloader(file, processing));
        }

        return sources;
    }

    private boolean isWarcFile(Path path) {
        return path.toString().endsWith(".warc")
            || path.toString().endsWith(".warc.gz");
    }
}