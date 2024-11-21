package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.construction.full.FullIndexConstructor;
import nu.marginalia.index.construction.prio.PrioIndexConstructor;
import nu.marginalia.index.domainrankings.DomainRankings;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.forward.construction.ForwardIndexConverter;
import nu.marginalia.index.journal.IndexJournal;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mqapi.index.CreateIndexRequest;
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
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static nu.marginalia.mqapi.ProcessInboxNames.INDEX_CONSTRUCTOR_INBOX;

public class IndexConstructorMain extends ProcessMainClass {
    private final FileStorageService fileStorageService;
    private final ProcessHeartbeatImpl heartbeat;
    private final DomainRankings domainRankings;

    private static final Logger logger = LoggerFactory.getLogger(IndexConstructorMain.class);

    public static void main(String[] args) throws Exception {
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

        super(messageQueueFactory, processConfiguration, GsonFactory.get(), INDEX_CONSTRUCTOR_INBOX);

        this.fileStorageService = fileStorageService;
        this.heartbeat = heartbeat;
        this.domainRankings = domainRankings;
    }

    private void run(CreateIndexRequest instructions) throws SQLException, IOException {
        heartbeat.start();

        switch (instructions.indexName()) {
            case FORWARD      -> createForwardIndex();
            case REVERSE_FULL -> createFullReverseIndex();
            case REVERSE_PRIO -> createPrioReverseIndex();
        }

        heartbeat.shutDown();
    }

    private void createFullReverseIndex() throws IOException {

        Path outputFileDocs = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.DOCS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.WORDS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFilePositions = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.POSITIONS, ReverseIndexFullFileNames.FileVersion.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        var constructor = new FullIndexConstructor(
                outputFileDocs,
                outputFileWords,
                outputFilePositions,
                this::addRankToIdEncoding,
                tmpDir);

        constructor.createReverseIndex(heartbeat, "createReverseIndexFull", workDir);

    }

    private void createPrioReverseIndex() throws IOException {

        Path outputFileDocs = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.DOCS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.WORDS, ReverseIndexPrioFileNames.FileVersion.NEXT);

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        var constructor = new PrioIndexConstructor(
                outputFileDocs,
                outputFileWords,
                this::addRankToIdEncoding,
                tmpDir);

        constructor.createReverseIndex(heartbeat, "createReverseIndexPrio", workDir);
    }

    private void createForwardIndex() throws IOException {

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);

        Path outputFileDocsId = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_ID, ForwardIndexFileNames.FileVersion.NEXT);
        Path outputFileDocsData = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_DATA, ForwardIndexFileNames.FileVersion.NEXT);
        Path outputFileSpansData = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.SPANS_DATA, ForwardIndexFileNames.FileVersion.NEXT);

        ForwardIndexConverter converter = new ForwardIndexConverter(heartbeat,
                outputFileDocsId,
                outputFileDocsData,
                outputFileSpansData,
                IndexJournal.findJournal(workDir).orElseThrow(),
                domainRankings
        );

        converter.convert();
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
