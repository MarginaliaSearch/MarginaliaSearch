package nu.marginalia.index;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.ProcessConfigurationModule;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.db.storage.model.FileStorage;
import nu.marginalia.db.storage.model.FileStorageType;
import nu.marginalia.index.construction.ReverseIndexConstructor;
import nu.marginalia.index.forward.ForwardIndexConverter;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSingleShotInbox;
import nu.marginalia.mqapi.index.CreateIndexRequest;
import nu.marginalia.mqapi.index.IndexName;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.service.module.DatabaseModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;

import static nu.marginalia.mqapi.ProcessInboxNames.INDEX_CONSTRUCTOR_INBOX;

public class IndexConstructorMain {
    private final FileStorageService fileStorageService;
    private final ProcessHeartbeatImpl heartbeat;
    private final MessageQueueFactory messageQueueFactory;
    private final DomainRankings domainRankings;
    private static final Logger logger = LoggerFactory.getLogger(IndexConstructorMain.class);
    private final Gson gson = GsonFactory.get();
    public static void main(String[] args) throws Exception {
        new org.mariadb.jdbc.Driver();

        var main = Guice.createInjector(
                new IndexConstructorModule(),
                new ProcessConfigurationModule("index-constructor"),
                new DatabaseModule())
                .getInstance(IndexConstructorMain.class);

        var instructions = main.fetchInstructions();

        try {
            main.run(instructions);
            instructions.ok();
        }
        catch (Exception ex) {
            logger.error("Constructor failed", ex);
            instructions.err();
        }

        TimeUnit.SECONDS.sleep(5);

        System.exit(0);
    }

    @Inject
    public IndexConstructorMain(FileStorageService fileStorageService,
                                ProcessHeartbeatImpl heartbeat,
                                MessageQueueFactory messageQueueFactory,
                                DomainRankings domainRankings) {

        this.fileStorageService = fileStorageService;
        this.heartbeat = heartbeat;
        this.messageQueueFactory = messageQueueFactory;
        this.domainRankings = domainRankings;
    }

    private void run(CreateIndexInstructions instructions) throws SQLException, IOException {
        heartbeat.start();

        switch (instructions.name) {
            case FORWARD -> createForwardIndex();
            case REVERSE_FULL -> createFullReverseIndex();
            case REVERSE_PRIO -> createPrioReverseIndex();
        }

        heartbeat.shutDown();
    }

    private void createFullReverseIndex() throws SQLException, IOException {

        FileStorage indexLive = fileStorageService.getStorageByType(FileStorageType.INDEX_LIVE);
        FileStorage indexStaging = fileStorageService.getStorageByType(FileStorageType.INDEX_STAGING);

        Path outputFileDocs = ReverseIndexFullFileNames.resolve(indexLive.asPath(), ReverseIndexFullFileNames.FileIdentifier.DOCS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexFullFileNames.resolve(indexLive.asPath(), ReverseIndexFullFileNames.FileIdentifier.WORDS, ReverseIndexFullFileNames.FileVersion.NEXT);

        Path tmpDir = indexStaging.asPath().resolve("tmp");
        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);


        new ReverseIndexConstructor(outputFileDocs, outputFileWords,
                IndexJournalReader::singleFile,
                this::addRankToIdEncoding, tmpDir)
                    .createReverseIndex(heartbeat, indexStaging.asPath());

    }

    private void createPrioReverseIndex() throws SQLException, IOException {

        FileStorage indexLive = fileStorageService.getStorageByType(FileStorageType.INDEX_LIVE);
        FileStorage indexStaging = fileStorageService.getStorageByType(FileStorageType.INDEX_STAGING);

        Path outputFileDocs = ReverseIndexPrioFileNames.resolve(indexLive.asPath(), ReverseIndexPrioFileNames.FileIdentifier.DOCS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexPrioFileNames.resolve(indexLive.asPath(), ReverseIndexPrioFileNames.FileIdentifier.WORDS, ReverseIndexPrioFileNames.FileVersion.NEXT);

        Path tmpDir = indexStaging.asPath().resolve("tmp");
        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        // The priority index only includes words that have bits indicating they are
        // important to the document.  This filter will act on the encoded {@see WordMetadata}
        LongPredicate wordMetaFilter = getPriorityIndexWordMetaFilter();

        new ReverseIndexConstructor(outputFileDocs, outputFileWords,
                (path) -> IndexJournalReader.singleFile(path).filtering(wordMetaFilter),
                this::addRankToIdEncoding, tmpDir)
                .createReverseIndex(heartbeat, indexStaging.asPath());
    }

    private static LongPredicate getPriorityIndexWordMetaFilter() {

        long highPriorityFlags =
                WordFlags.Title.asBit()
                        | WordFlags.Subjects.asBit()
                        | WordFlags.TfIdfHigh.asBit()
                        | WordFlags.NamesWords.asBit()
                        | WordFlags.UrlDomain.asBit()
                        | WordFlags.UrlPath.asBit()
                        | WordFlags.Site.asBit()
                        | WordFlags.SiteAdjacent.asBit();

        return r -> WordMetadata.hasAnyFlags(r, highPriorityFlags);
    }

    private void createForwardIndex() throws SQLException, IOException {

        FileStorage indexLive = fileStorageService.getStorageByType(FileStorageType.INDEX_LIVE);
        FileStorage indexStaging = fileStorageService.getStorageByType(FileStorageType.INDEX_STAGING);

        Path outputFileDocsId = ForwardIndexFileNames.resolve(indexLive.asPath(), ForwardIndexFileNames.FileIdentifier.DOC_ID, ForwardIndexFileNames.FileVersion.NEXT);
        Path outputFileDocsData = ForwardIndexFileNames.resolve(indexLive.asPath(), ForwardIndexFileNames.FileIdentifier.DOC_DATA, ForwardIndexFileNames.FileVersion.NEXT);

        ForwardIndexConverter converter = new ForwardIndexConverter(heartbeat,
                IndexJournalReader.paging(indexStaging.asPath()),
                outputFileDocsId,
                outputFileDocsData,
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

    private static class CreateIndexInstructions {

        public final IndexName name;
        private final MqSingleShotInbox inbox;
        private final MqMessage message;

        private CreateIndexInstructions(IndexName name, MqSingleShotInbox inbox, MqMessage message) {
            this.name = name;
            this.inbox = inbox;
            this.message = message;
        }

        public void ok() {
            inbox.sendResponse(message, MqInboxResponse.ok());
        }
        public void err() {
            inbox.sendResponse(message, MqInboxResponse.err());
        }
    }

    private CreateIndexInstructions fetchInstructions() throws Exception {

        var inbox = messageQueueFactory.createSingleShotInbox(INDEX_CONSTRUCTOR_INBOX, UUID.randomUUID());

        logger.info("Waiting for instructions");
        var msgOpt = getMessage(inbox, CreateIndexRequest.class.getSimpleName());
        var msg = msgOpt.orElseThrow(() -> new RuntimeException("No message received"));

        var payload = gson.fromJson(msg.payload(), CreateIndexRequest.class);
        var name = payload.indexName();

        return new CreateIndexInstructions(name, inbox, msg);
    }

    private Optional<MqMessage> getMessage(MqSingleShotInbox inbox, String expectedFunction) throws SQLException, InterruptedException {
        var opt = inbox.waitForMessage(30, TimeUnit.SECONDS);
        if (opt.isPresent()) {
            if (!opt.get().function().equals(expectedFunction)) {
                throw new RuntimeException("Unexpected function: " + opt.get().function());
            }
            return opt;
        }
        else {
            var stolenMessage = inbox.stealMessage(msg -> msg.function().equals(expectedFunction));
            stolenMessage.ifPresent(mqMessage -> logger.info("Stole message {}", mqMessage));
            return stolenMessage;
        }
    }
}
