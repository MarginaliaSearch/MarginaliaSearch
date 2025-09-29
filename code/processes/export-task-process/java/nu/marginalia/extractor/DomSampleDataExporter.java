package nu.marginalia.extractor;

import com.google.inject.Inject;
import nu.marginalia.domsample.db.DomSampleDb;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class DomSampleDataExporter {
    private final FileStorageService storageService;
    private static final Logger logger = LoggerFactory.getLogger(DomSampleDataExporter.class);

    @Inject
    public DomSampleDataExporter(FileStorageService storageService) {
        this.storageService = storageService;
    }

    private record ExportModel(String domain, boolean acceptedPopover, String sample, List<String> requests) {
        public ExportModel(DomSampleDb.Sample sample) {
           this(sample.domain(),
                   sample.acceptedPopover(),
                   sample.sample(),
                   Arrays.asList(StringUtils.split(sample.requests(), "\n")));
        }

    }
    public void export(FileStorageId destId) throws Exception {
        FileStorage destStorage = storageService.getStorage(destId);

        try (DomSampleDb domSampleDb = new DomSampleDb();
             JsonLWriter<ExportModel> jsonLWriter =
                     new JsonLWriter<>(destStorage.asPath(), "dom-samples", 100_000)) {
            domSampleDb.forEachSample(sample -> {
                try {
                    jsonLWriter.write(new ExportModel(sample));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return true;
            });
        }
    }

}
