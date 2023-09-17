package nu.marginalia.converting.sideload.dirtree;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class DirtreeSideloaderFactory {
    private final SideloaderProcessing sideloaderProcessing;

    @Inject
    public DirtreeSideloaderFactory(SideloaderProcessing sideloaderProcessing) {
        this.sideloaderProcessing = sideloaderProcessing;
    }

    public Collection<DirtreeSideloader> createSideloaders(Path specsYaml) throws IOException {
        var yaml = new Yaml();

        List<DirtreeSideloader> ret = new ArrayList<>();
        Path basePath = specsYaml.getParent();

        try (var reader = new FileReader(specsYaml.toFile())) {
            var specsList = yaml.loadAs(reader,
                    DirtreeSideloadSpecList.class);

            for (var source : specsList.sources) {
                ret.add(createSideloader(basePath.resolve(source.dir),
                        source,
                        sideloaderProcessing));
            }
        }

        return ret;
    }

    private DirtreeSideloader createSideloader(Path basePath, DirtreeSideloadSpec source, SideloaderProcessing sideloaderProcessing) throws IOException {
        return new DirtreeSideloader(
                basePath,
                source.domainName,
                source.baseUrl,
                source.keywords,
                sideloaderProcessing
        );
    }
}
