package nu.marginalia.index.svc;

import com.google.inject.Guice;
import com.google.inject.Inject;
import nu.marginalia.index.client.model.query.EdgeSearchSpecification;
import nu.marginalia.index.client.model.query.EdgeSearchSubquery;
import nu.marginalia.index.client.model.query.SearchSetIdentifier;
import nu.marginalia.index.client.model.results.EdgeSearchResultItem;
import nu.marginalia.index.index.SearchIndex;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.index.journal.model.IndexJournalEntryHeader;
import nu.marginalia.index.journal.writer.IndexJournalWriter;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.lexicon.KeywordLexicon;
import nu.marginalia.model.crawl.EdgePageWordFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.service.server.Initialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import spark.Spark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
public class IndexQueryServiceIntegrationTest {

    @Inject
    Initialization initialization;

    IndexQueryServiceIntegrationTestModule testModule;

    @Inject
    IndexQueryService queryService;
    @Inject
    SearchIndex searchIndex;

    @Inject
    KeywordLexicon keywordLexicon;

    @Inject
    IndexJournalWriter indexJournalWriter;

    @BeforeEach
    public void setUp() throws IOException {

        testModule = new IndexQueryServiceIntegrationTestModule();
        Guice.createInjector(testModule).injectMembers(this);

        initialization.setReady();
    }

    @AfterEach
    public void tearDown() throws IOException {
        testModule.cleanUp();

        Spark.stop();
    }

    @Test
    public void willItBlend() throws Exception {
        for (int i = 1; i < 512; i++) {
            loadData(i);
        }

        indexJournalWriter.close();
        searchIndex.switchIndex();

        var rsp = queryService.justQuery(
                EdgeSearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .year(SpecificationLimit.none())
                        .quality(SpecificationLimit.none())
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .domains(new ArrayList<>())
                        .searchSetIdentifier(SearchSetIdentifier.NONE)
                        .subqueries(List.of(new EdgeSearchSubquery(
                                List.of("3", "5", "2"), List.of("4"), Collections.emptyList(), Collections.emptyList()
                        ))).build());

        Assertions.assertArrayEquals(
                new int[] { 30, 90, 150, 210, 270, 330, 390, 450, 510 },
                rsp.results
                        .stream()
                        .mapToInt(EdgeSearchResultItem::getUrlIdInt)
                        .toArray());
    }


    @Test
    public void testDomainQuery() throws Exception {
        for (int i = 1; i < 512; i++) {
            loadDataWithDomain(i/100, i);
        }

        indexJournalWriter.close();
        searchIndex.switchIndex();

        var rsp = queryService.justQuery(
                EdgeSearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .year(SpecificationLimit.none())
                        .quality(SpecificationLimit.none())
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .domains(List.of(2))
                        .subqueries(List.of(new EdgeSearchSubquery(
                                List.of("3", "5", "2"), List.of("4"), Collections.emptyList(), Collections.emptyList()
                        ))).build());
        Assertions.assertArrayEquals(
                new int[] { 210, 270 },
                rsp.results.stream().mapToInt(EdgeSearchResultItem::getUrlIdInt).toArray());
    }

    @Test
    public void testYearQuery() throws Exception {
        for (int i = 1; i < 512; i++) {
            loadData(i);
        }
        indexJournalWriter.close();
        searchIndex.switchIndex();

        var rsp = queryService.justQuery(
                EdgeSearchSpecification.builder()
                        .queryLimits(new QueryLimits(10, 10, Integer.MAX_VALUE, 4000))
                        .quality(SpecificationLimit.none())
                        .year(SpecificationLimit.equals(1998))
                        .size(SpecificationLimit.none())
                        .rank(SpecificationLimit.none())
                        .queryStrategy(QueryStrategy.SENTENCE)
                        .searchSetIdentifier(SearchSetIdentifier.NONE)
                        .subqueries(List.of(new EdgeSearchSubquery(
                                List.of("4"), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
                        ))
                        ).build());

        Assertions.assertArrayEquals(
                new int[] { 12, 72, 132, 192, 252, 312, 372, 432, 492, 32 },
                rsp.results.stream().mapToInt(EdgeSearchResultItem::getUrlIdInt).toArray());
    }



    public void loadData(int id) {
        int[] factors = IntStream
                .rangeClosed(1, id)
                .filter(v -> (id % v) == 0)
                .toArray();

        long fullId = id | ((long) (32 - (id % 32)) << 32);

        var header = new IndexJournalEntryHeader(factors.length, fullId, new DocumentMetadata(0, 0, 0, id % 5, id, id % 20, (byte) 0).encode());

        long[] data = new long[factors.length*2];
        for (int i = 0; i < factors.length; i++) {
            data[2*i] = keywordLexicon.getOrInsert(Integer.toString(factors[i]));
            data[2*i + 1] = new WordMetadata(i, i, EnumSet.of(EdgePageWordFlags.Title)).encode();
        }

        indexJournalWriter.put(header, new IndexJournalEntryData(data));
    }

    public void loadDataWithDomain(int domain, int id) {
        int[] factors = IntStream.rangeClosed(1, id).filter(v -> (id % v) == 0).toArray();
        var header = new IndexJournalEntryHeader(factors.length, id | ((long) domain << 32), DocumentMetadata.defaultValue());

        long[] data = new long[factors.length*2];
        for (int i = 0; i < factors.length; i++) {
            data[2*i] = keywordLexicon.getOrInsert(Integer.toString(factors[i]));
            data[2*i + 1] = new WordMetadata(i % 20, i, EnumSet.of(EdgePageWordFlags.Title)).encode();
        }

        indexJournalWriter.put(header, new IndexJournalEntryData(data));
    }
}
