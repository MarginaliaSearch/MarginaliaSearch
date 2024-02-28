package nu.marginalia.index;

import com.google.inject.Guice;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.IndexLocations;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.query.SearchSubquery;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.process.control.FakeProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.index.construction.DocIdRewriter;
import nu.marginalia.index.construction.ReverseIndexConstructor;
import nu.marginalia.index.forward.ForwardIndexConverter;
import nu.marginalia.index.forward.ForwardIndexFileNames;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.index.domainrankings.DomainRankings;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.server.Initialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.IntStream;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class IndexQueryServiceIntegrationSmokeTest {

    @Inject
    Initialization initialization;

    IndexQueryServiceIntegrationTestModule testModule;

    @Inject
    IndexGrpcService queryService;
    @Inject
    StatefulIndex statefulIndex;

    @Inject
    ServiceHeartbeat heartbeat;

    @Inject
    IndexJournalWriter indexJournalWriter;

    @Inject
    FileStorageService fileStorageService;

    @Inject
    DomainRankings domainRankings;

    @Inject
    DocumentDbReader documentDbReader;

    @Inject
    ProcessHeartbeat processHeartbeat;

    @BeforeEach
    public void setUp() throws IOException {

        testModule = new IndexQueryServiceIntegrationTestModule();
        Guice.createInjector(testModule).injectMembers(this);

        initialization.setReady();
    }

    @AfterEach
    public void tearDown() throws IOException {
        testModule.cleanUp();
    }

    @Test
    public void willItBlend() throws Exception {
        var linkdbWriter = new DocumentDbWriter(
                IndexLocations.getLinkdbLivePath(fileStorageService)
                        .resolve(DOCDB_FILE_NAME)
        );
        for (int i = 1; i < 512; i++) {
            loadData(linkdbWriter, i);
        }
        linkdbWriter.close();
        documentDbReader.reconnect();

        indexJournalWriter.close();
        constructIndex();
        statefulIndex.switchIndex();

        var rsp = queryService.justQuery(
                SearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .year(SpecificationLimit.none())
                        .quality(SpecificationLimit.none())
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .rankingParams(ResultRankingParameters.sensibleDefaults())
                        .domains(new ArrayList<>())
                        .searchSetIdentifier("NONE")
                        .subqueries(List.of(new SearchSubquery(
                                List.of("3", "5", "2"), List.of("4"), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList()))).build());

        int[] idxes = new int[] { 30, 510, 90, 150, 210, 270, 330, 390, 450 };
        long[] ids = IntStream.of(idxes).mapToLong(this::fullId).toArray();
        long[] actual = rsp.results
                .stream()
                .mapToLong(i -> i.rawIndexResult.getDocumentId())
                .toArray();

        System.out.println(Arrays.toString(actual));
        System.out.println(Arrays.toString(ids));
        Assertions.assertArrayEquals(ids, actual);
    }

    @Test
    public void testDomainQuery() throws Exception {

        var linkdbWriter = new DocumentDbWriter(
                IndexLocations.getLinkdbLivePath(fileStorageService)
                        .resolve(DOCDB_FILE_NAME)
        );
        for (int i = 1; i < 512; i++) {
            loadDataWithDomain(linkdbWriter, i/100, i);
        }
        linkdbWriter.close();
        documentDbReader.reconnect();

        indexJournalWriter.close();
        constructIndex();
        statefulIndex.switchIndex();

        var rsp = queryService.justQuery(
                SearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .year(SpecificationLimit.none())
                        .quality(SpecificationLimit.none())
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .rankingParams(ResultRankingParameters.sensibleDefaults())
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .domains(List.of(2))
                        .subqueries(List.of(new SearchSubquery(
                                List.of("3", "5", "2"), List.of("4"), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList()))).build());
        int[] idxes = new int[] {  210, 270 };
        long[] ids = IntStream.of(idxes).mapToLong(id -> UrlIdCodec.encodeId(id/100, id)).toArray();
        long[] actual = rsp.results.stream().mapToLong(i -> i.rawIndexResult.getDocumentId()).toArray();

        Assertions.assertArrayEquals(ids, actual);
    }

    @Test
    public void testYearQuery() throws Exception {
        var linkdbWriter = new DocumentDbWriter(
                IndexLocations.getLinkdbLivePath(fileStorageService)
                        .resolve(DOCDB_FILE_NAME)
        );
        for (int i = 1; i < 512; i++) {
            loadData(linkdbWriter, i);
        }
        linkdbWriter.close();
        documentDbReader.reconnect();

        indexJournalWriter.close();
        constructIndex();
        statefulIndex.switchIndex();

        var rsp = queryService.justQuery(
                SearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .quality(SpecificationLimit.none())
                        .year(SpecificationLimit.equals(1998))
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .searchSetIdentifier("NONE")
                        .rankingParams(ResultRankingParameters.sensibleDefaults())
                        .subqueries(List.of(new SearchSubquery(
                                List.of("4"), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList()))
                        ).build());


        Set<Integer> years = new HashSet<>();

        for (var res : rsp.results) {
            for (var score : res.rawIndexResult.getKeywordScores()) {
                years.add(DocumentMetadata.decodeYear(score.encodedDocMetadata()));
            }
        }

        assertEquals(Set.of(1998), years);
        assertEquals(rsp.results.size(), 10);

    }


    private void constructIndex() throws SQLException, IOException {
        createForwardIndex();
        createFullReverseIndex();
        createPrioReverseIndex();
    }


    private void createFullReverseIndex() throws SQLException, IOException {

        Path outputFileDocs = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.DOCS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexFullFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexFullFileNames.FileIdentifier.WORDS, ReverseIndexFullFileNames.FileVersion.NEXT);
        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        new ReverseIndexConstructor(outputFileDocs, outputFileWords, IndexJournalReader::singleFile, DocIdRewriter.identity(), tmpDir)
                .createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createPrioReverseIndex() throws SQLException, IOException {

        Path outputFileDocs = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.DOCS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path outputFileWords = ReverseIndexPrioFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ReverseIndexPrioFileNames.FileIdentifier.WORDS, ReverseIndexPrioFileNames.FileVersion.NEXT);
        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path tmpDir = workDir.resolve("tmp");

        if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

        new ReverseIndexConstructor(outputFileDocs, outputFileWords, IndexJournalReader::singleFile, DocIdRewriter.identity(), tmpDir)
                .createReverseIndex(new FakeProcessHeartbeat(), "name", workDir);
    }

    private void createForwardIndex() throws SQLException, IOException {

        Path workDir = IndexLocations.getIndexConstructionArea(fileStorageService);
        Path outputFileDocsId = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_ID, ForwardIndexFileNames.FileVersion.NEXT);
        Path outputFileDocsData = ForwardIndexFileNames.resolve(IndexLocations.getCurrentIndex(fileStorageService), ForwardIndexFileNames.FileIdentifier.DOC_DATA, ForwardIndexFileNames.FileVersion.NEXT);

        ForwardIndexConverter converter = new ForwardIndexConverter(processHeartbeat,
                IndexJournalReader.paging(workDir),
                outputFileDocsId,
                outputFileDocsData,
                domainRankings
        );

        converter.convert();
    }

    private long fullId(int id) {
        return UrlIdCodec.encodeId((32 - (id % 32)), id);
    }

    MurmurHash3_128 hasher = new MurmurHash3_128();
    @SneakyThrows
    public void loadData(DocumentDbWriter ldbw, int id) {
        int[] factors = IntStream
                .rangeClosed(1, id)
                .filter(v -> (id % v) == 0)
                .toArray();

        long fullId = fullId(id);

        var header = new IndexJournalEntryHeader(factors.length, 0, fullId, new DocumentMetadata(0, 0, 0, 0, id % 5, id, id % 20, (byte) 0).encode());

        long[] data = new long[factors.length * 2];
        for (int i = 0; i < factors.length; i++) {
            data[2 * i] = hasher.hashNearlyASCII(Integer.toString(factors[i]));
            data[2 * i + 1] = new WordMetadata(i, EnumSet.of(WordFlags.Title)).encode();
        }

        ldbw.add(new DocdbUrlDetail(
                fullId, new EdgeUrl("https://www.example.com/"+id),
                "test", "test", 0., "HTML5", 0, null, 0, 10
        ));

        indexJournalWriter.put(header, new IndexJournalEntryData(data));
    }

    @SneakyThrows
    public void loadDataWithDomain(DocumentDbWriter ldbw, int domain, int id) {
        int[] factors = IntStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();
        long fullId = UrlIdCodec.encodeId(domain, id);
        var header = new IndexJournalEntryHeader(factors.length, 0, fullId, DocumentMetadata.defaultValue());

        long[] data = new long[factors.length*2];
        for (int i = 0; i < factors.length; i++) {
            data[2*i] = hasher.hashNearlyASCII(Integer.toString(factors[i]));
            data[2*i + 1] = new WordMetadata(i, EnumSet.of(WordFlags.Title)).encode();
        }

        ldbw.add(new DocdbUrlDetail(
                fullId, new EdgeUrl("https://www.example.com/"+id),
                "test", "test", 0., "HTML5", 0, null, 0, 10
        ));


        indexJournalWriter.put(header, new IndexJournalEntryData(data));
    }

}
