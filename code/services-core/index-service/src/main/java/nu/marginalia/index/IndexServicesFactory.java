package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.index.SearchIndexReader;
import nu.marginalia.service.control.ServiceHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;

@Singleton
public class IndexServicesFactory {
    private final Path liveStorage;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Path searchSetsBase;

    final int LIVE_PART = 0;
    final int NEXT_PART = 1;

    @Inject
    public IndexServicesFactory(
            ServiceHeartbeat heartbeat,
            FileStorageService fileStorageService
            ) throws IOException, SQLException {

        liveStorage = fileStorageService.getStorageByType(FileStorageType.INDEX_LIVE).asPath();
        searchSetsBase = fileStorageService.getStorageByType(FileStorageType.SEARCH_SETS).asPath();

    }

    public Path getSearchSetsBase() {
        return searchSetsBase;
    }

    public ReverseIndexReader getReverseIndexReader() throws IOException {

        return new ReverseIndexReader(
                ReverseIndexFullFileNames.resolve(liveStorage, ReverseIndexFullFileNames.FileIdentifier.WORDS, ReverseIndexFullFileNames.FileVersion.CURRENT),
                ReverseIndexFullFileNames.resolve(liveStorage, ReverseIndexFullFileNames.FileIdentifier.DOCS, ReverseIndexFullFileNames.FileVersion.CURRENT)
        );
    }

    public ReverseIndexReader getReverseIndexPrioReader() throws IOException {
        return new ReverseIndexReader(
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

    public SearchIndexReader getSearchIndexReader() throws IOException {
        return new SearchIndexReader(
                getForwardIndexReader(),
                getReverseIndexReader(),
                getReverseIndexPrioReader()
        );
    }
}
