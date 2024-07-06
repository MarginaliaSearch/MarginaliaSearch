package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.positions.PositionsFileReader;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.forward.ForwardIndexReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Singleton
public class IndexFactory {
    private final FileStorageService fileStorageService;
    private final Path liveStorage;

    @Inject
    public IndexFactory(FileStorageService fileStorageService) {

        this.fileStorageService = fileStorageService;
        this.liveStorage = IndexLocations.getCurrentIndex(fileStorageService);
    }

    public CombinedIndexReader getCombinedIndexReader() throws IOException {
        return new CombinedIndexReader(
                getForwardIndexReader(),
                getReverseIndexReader(),
                getReverseIndexPrioReader()
        );
    }

    public Path getSearchSetsBase() {
        return IndexLocations.getSearchSetsPath(fileStorageService);
    }

    public FullReverseIndexReader getReverseIndexReader() throws IOException {
        return new FullReverseIndexReader("full",
                ReverseIndexFullFileNames.resolve(liveStorage, ReverseIndexFullFileNames.FileIdentifier.WORDS, ReverseIndexFullFileNames.FileVersion.CURRENT),
                ReverseIndexFullFileNames.resolve(liveStorage, ReverseIndexFullFileNames.FileIdentifier.DOCS, ReverseIndexFullFileNames.FileVersion.CURRENT),
                new PositionsFileReader(ReverseIndexFullFileNames.resolve(liveStorage, ReverseIndexFullFileNames.FileIdentifier.POSITIONS, ReverseIndexFullFileNames.FileVersion.CURRENT))
        );
    }

    public PrioReverseIndexReader getReverseIndexPrioReader() throws IOException {
        return new PrioReverseIndexReader("prio",
                ReverseIndexPrioFileNames.resolve(liveStorage, ReverseIndexPrioFileNames.FileIdentifier.WORDS, ReverseIndexPrioFileNames.FileVersion.CURRENT),
                ReverseIndexPrioFileNames.resolve(liveStorage, ReverseIndexPrioFileNames.FileIdentifier.DOCS, ReverseIndexPrioFileNames.FileVersion.CURRENT)
        );
    }

    public ForwardIndexReader getForwardIndexReader() throws IOException {
        return new ForwardIndexReader(
                ForwardIndexFileNames.resolve(liveStorage, ForwardIndexFileNames.FileIdentifier.DOC_ID, ForwardIndexFileNames.FileVersion.CURRENT),
                ForwardIndexFileNames.resolve(liveStorage, ForwardIndexFileNames.FileIdentifier.DOC_DATA, ForwardIndexFileNames.FileVersion.CURRENT)
        );
    }

    /** Switches the current index to the next index */
    public void switchFiles() throws IOException {

        for (var file : ReverseIndexFullFileNames.FileIdentifier.values()) {
            switchFile(
                    ReverseIndexFullFileNames.resolve(liveStorage, file, ReverseIndexFullFileNames.FileVersion.NEXT),
                    ReverseIndexFullFileNames.resolve(liveStorage, file, ReverseIndexFullFileNames.FileVersion.CURRENT)
            );
        }
        for (var file : ReverseIndexPrioFileNames.FileIdentifier.values()) {
            switchFile(
                    ReverseIndexPrioFileNames.resolve(liveStorage, file, ReverseIndexPrioFileNames.FileVersion.NEXT),
                    ReverseIndexPrioFileNames.resolve(liveStorage, file, ReverseIndexPrioFileNames.FileVersion.CURRENT)
            );
        }
        for (var file : ForwardIndexFileNames.FileIdentifier.values()) {
            switchFile(
                    ForwardIndexFileNames.resolve(liveStorage, file, ForwardIndexFileNames.FileVersion.NEXT),
                    ForwardIndexFileNames.resolve(liveStorage, file, ForwardIndexFileNames.FileVersion.CURRENT)
            );
        }
    }

    public void switchFile(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }


}
