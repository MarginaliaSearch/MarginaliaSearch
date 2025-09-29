package nu.marginalia.extractor;

import com.google.inject.Inject;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DomSampleDataExporter {
    private final FileStorageService storageService;
    private static final Logger logger = LoggerFactory.getLogger(DomSampleDataExporter.class);

    @Inject
    public DomSampleDataExporter(FileStorageService storageService) {
        this.storageService = storageService;
    }

    public void export(FileStorageId destId) throws Exception {
        FileStorage destStorage = storageService.getStorage(destId);
        // TODO:  Draw the rest of the owl
    }

}
