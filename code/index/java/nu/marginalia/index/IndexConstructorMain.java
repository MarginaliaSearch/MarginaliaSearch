package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.config.IndexFileName;
import nu.marginalia.index.forward.construction.ForwardIndexConverter;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.index.reverse.construction.full.FullIndexConstructor;
import nu.marginalia.index.reverse.construction.prio.PrioIndexConstructor;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.ProcessInboxNames;
import nu.marginalia.mqapi.index.CreateIndexRequest;
import nu.marginalia.mqapi.index.IndexName;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.process.ProcessMainClass;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class IndexConstructorMain extends ProcessMainClass {
    private final FileStorageService fileStorageService;
    private final ProcessHeartbeatImpl heartbeat;
    private final DomainRankings domainRankings;

    private static final Logger logger = LoggerFactory.getLogger(IndexConstructorMain.class);

    static void main(String[] args) throws Exception {
        Instructions<CreateIndexRequest> instructions = null;
        try {
            new org.mariadb.jdbc.Driver();

            var main = Guice.createInjector(
                            new IndexConstructorModule(),
                            new ProcessConfigurationModule("index-constructor"),
                            new DatabaseModule(false))
                    .getInstance(IndexConstructorMain.class);

            instructions = main.fetchInstructions(CreateIndexRequest.class);
            main.run(instructions.value());
            instructions.ok();
        }
        catch (Exception ex) {
            logger.error("Constructor failed", ex);

            if (instructions != null) {
                instructions.err();
            }
        }

        // Grace period so we don't rug pull the logger or jdbc
        TimeUnit.SECONDS.sleep(5);


        System.exit(0);
    }

    @Inject
    public IndexConstructorMain(FileStorageService fileStorageService,
                                ProcessHeartbeatImpl heartbeat,
                                MessageQueueFactory messageQueueFactory,
                                ProcessConfiguration processConfiguration,
                                DomainRankings domainRankings) {

        super(messageQueueFactory, processConfiguration, GsonFactory.get(), ProcessInboxNames.INDEX_CONSTRUCTOR_INBOX);

        this.fileStorageService = fileStorageService;
        this.heartbeat = heartbeat;
        this.domainRankings = domainRankings;
    }

    private void run(CreateIndexRequest instructions) throws IOException {
        heartbeat.start();

        switch (instructions.indexName()) {
            case IndexName.FORWARD      -> createForwardIndex();
            case IndexName.REVERSE_FULL -> createFullReverseIndex();
            case IndexName.REVERSE_PRIO -> createPrioReverseIndex();
        }

        heartbeat.shutDown();
    }

    private void createFullReverseIndex() throws IOException {

        Path outputFileDocs = findNextFile(new IndexFileName.FullDocs());
        Path outputFilePositions = findNextFile(new IndexFileName.FullPositions());

        Files.deleteIfExists(outputFileDocs);
        Files.deleteIfExists(outputFilePositions);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        Set<String> languageIsoCodes = IndexJournal.findJournal(workDir)
                .map(IndexJournal::languages)
                .orElseGet(Set::of);

        for (String languageIsoCode : languageIsoCodes) {
            Path outputFileWords = findNextFile(new IndexFileName.FullWords(languageIsoCode));

            FullIndexConstructor constructor = new FullIndexConstructor(
                    languageIsoCode,
                    outputFileDocs,
                    outputFileWords,
                    outputFilePositions,
                    this::addRankToIdEncoding,
                    tmpDir);

            String processName = "createReverseIndexFull[%s]".formatted(languageIsoCode);

            constructor.createReverseIndex(heartbeat, processName, workDir);
        }
    }

    private void createPrioReverseIndex() throws IOException {

        Path outputFileDocs = findNextFile(new IndexFileName.PrioDocs());
        Files.deleteIfExists(outputFileDocs);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        Set<String> languageIsoCodes = IndexJournal.findJournal(workDir)
                .map(IndexJournal::languages)
                .orElseGet(Set::of);

        for (String languageIsoCode : languageIsoCodes) {
            Path outputFileWords = findNextFile(new IndexFileName.PrioWords(languageIsoCode));
            Files.deleteIfExists(outputFileWords);

            PrioIndexConstructor constructor = new PrioIndexConstructor(
                    languageIsoCode,
                    outputFileDocs,
                    outputFileWords,
                    this::addRankToIdEncoding,
                    tmpDir);

            String processName = "createReverseIndexPrio[%s]".formatted(languageIsoCode);

            constructor.createReverseIndex(heartbeat, processName, workDir);
        }
    }

    private void createForwardIndex() throws IOException {

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);

        Path outputFileDocsId = findNextFile(new IndexFileName.ForwardDocIds());
        Path outputFileDocsData = findNextFile(new IndexFileName.ForwardDocData());
        Path outputFileSpansData = findNextFile(new IndexFileName.ForwardSpansData());

        ForwardIndexConverter converter = new ForwardIndexConverter(heartbeat,
                outputFileDocsId,
                outputFileDocsData,
                outputFileSpansData,
                IndexJournal.findJournal(workDir).orElseThrow(),
                domainRankings
        );

        converter.convert();
    }

    private Path findNextFile(IndexFileName fileName) {
        return IndexFileName.resolve(IndexLocations.getCurrentIndex(fileStorageService), fileName, IndexFileName.Version.NEXT);
    }

    /** Append the domain's ranking to the high bits of a document ID
     * to ensure they're sorted in order of rank within the index.
     */
    private long addRankToIdEncoding(long docId) {
        return UrlIdCodec.addRank(
                domainRankings.getSortRanking(docId),
                docId);
    }

}
