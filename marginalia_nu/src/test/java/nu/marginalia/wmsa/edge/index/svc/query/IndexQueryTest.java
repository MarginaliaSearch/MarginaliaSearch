package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.util.btree.BTreeWriter;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexURLRange;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterAnyOf;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterBTreeRangeReject;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterBTreeRangeRetain;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class IndexQueryTest {
    static Path file;

    static long threesOffset;
    static long fivesOffset;
    static long sevensOffset;
    static long smallSeventeenOffset;

    // sz should be large enough to ensure the tree has multiple layers to shake out bugs
    static int sz = 128*512*512*2;

    static MultimapFileLong mmf;
    @BeforeAll
    static void setUpAll() throws IOException {
        file = Files.createTempFile(IndexQueryTest.class.getSimpleName(), ".dat");

        try (var mmf = MultimapFileLong.forOutput(file, 10_000_000)) {
            var bTreeWriter = new BTreeWriter(mmf, SearchIndexConverter.urlsBTreeContext);

            threesOffset = 0;
            long written = 0;
            written = bTreeWriter.write(0, sz / 2, w -> {
                for (int i = 0; i < sz; i+=2) {
                    w.put(i, 3L*(i/2));
                    w.put(i+1, i/2);
                }
            });

            fivesOffset += written;
            sevensOffset += written;
            smallSeventeenOffset += written;

            written = bTreeWriter.write(fivesOffset, sz/2, w -> {
                for (int i = 0; i < sz; i+=2) {
                    w.put(i, 5L*(i/2));
                    w.put(i+1, (i/2));
                }
            });

            sevensOffset += written;
            smallSeventeenOffset += written;

            written = bTreeWriter.write(sevensOffset, sz / 2, w -> {
                for (int i = 0; i < sz; i+=2) {
                    w.put(i, 7L*(i/2));
                    w.put(i+1, (i/2));
                }
            });

            smallSeventeenOffset += written;

            written = bTreeWriter.write(smallSeventeenOffset, 100, w -> {
                for (int i = 0; i < 200; i+=2) {
                    w.put(i, 17L*(i/2));
                    w.put(i+1, (i/2));
                }
            });
        }

        mmf = MultimapFileLong.forReading(file);


    }

    public SearchIndexURLRange threesRange() {
        return new SearchIndexURLRange(mmf, threesOffset);
    }
    public SearchIndexURLRange fivesRange() {
        return new SearchIndexURLRange(mmf, fivesOffset);
    }
    public SearchIndexURLRange sevensRange() {
        return new SearchIndexURLRange(mmf, sevensOffset);
    }
    public SearchIndexURLRange seventeensRange() {
        return new SearchIndexURLRange(mmf, smallSeventeenOffset);
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        mmf.close();
        Files.deleteIfExists(file);
    }

    @Test
    public void testMergeRanges() {
        BTreeQueryBuffer buffer = new BTreeQueryBuffer(300);

        IndexQuery query = new IndexQuery(List.of(seventeensRange().asEntrySource(), threesRange().asEntrySource()));

        /** Read from 17s range */

        // 17s range is shorter and should read fully in one go

        query.getMoreResults(buffer);
        assertFalse(buffer.isEmpty());
        assertArrayEquals(LongStream.range(0, 100).map(l -> l*17).toArray(), buffer.copyData());

        /** Read from 3s range */

        assertTrue(query.hasMore());
        query.getMoreResults(buffer);
        assertArrayEquals(LongStream.range(0, 150).map(l -> l*3).toArray(), buffer.copyData());

        /** Ensure 3s range is not flagged as finished */

        assertFalse(buffer.isEmpty());
        assertTrue(query.hasMore());
    }

    @Test
    public void test() {
        BTreeQueryBuffer buffer = new BTreeQueryBuffer(300);

        IndexQuery query = new IndexQuery(List.of(threesRange().asPrefixSource(102, 200)));

        /** Read from 17s range */

        // 17s range is shorter and should read fully in one go

        query.getMoreResults(buffer);
        System.out.println(Arrays.toString(buffer.copyData()));
        assertFalse(buffer.isEmpty());
        assertArrayEquals(LongStream.range(0, 100).map(l -> l*17).toArray(), buffer.copyData());

    }

    @Test
    public void testInclude() {
        BTreeQueryBuffer buffer = new BTreeQueryBuffer(300);

        /** Set up filters */
        var es = threesRange().asEntrySource();
        es.skip(10000);
        IndexQuery query = new IndexQuery(List.of(es));

        query.addInclusionFilter(new QueryFilterBTreeRangeRetain(fivesRange()));
        query.addInclusionFilter(new QueryFilterBTreeRangeRetain(sevensRange()));

        /** Do it */
        query.getMoreResults(buffer);
        assertArrayEquals(LongStream.range(10000, 10150)
                .map(l -> l*3)
                .filter(l -> (l % 5) == 0)
                .filter(l -> (l % 7) == 0)
                .toArray(), buffer.copyData());
    }

    @Test
    public void testIncludeReject() {
        BTreeQueryBuffer buffer = new BTreeQueryBuffer(300);

        /** Set up filters */
        var es = threesRange().asEntrySource();
        es.skip(10000);
        IndexQuery query = new IndexQuery(List.of(es));

        query.addInclusionFilter(new QueryFilterBTreeRangeRetain(fivesRange()));
        query.addInclusionFilter(new QueryFilterBTreeRangeReject(sevensRange()));

        /** Do it */
        query.getMoreResults(buffer);
        assertArrayEquals(LongStream.range(10000, 10150)
                .map(l -> l*3)
                .filter(l -> (l % 5) == 0)
                .filter(l -> (l % 7) != 0)
                .toArray(), buffer.copyData());
    }


    @Test
    public void testIncludeEither() {
        BTreeQueryBuffer buffer = new BTreeQueryBuffer(300);

        /** Set up filters */
        var es = threesRange().asEntrySource();
        es.skip(10000);
        IndexQuery query = new IndexQuery(List.of(es));
        query.addInclusionFilter(new QueryFilterAnyOf(
                List.of(new QueryFilterBTreeRangeRetain(fivesRange()),
                        new QueryFilterBTreeRangeRetain(sevensRange()))));

        /** Do it */
        query.getMoreResults(buffer);
        assertArrayEquals(LongStream.range(10000, 10150)
                .map(l -> l*3)
                .filter(l -> (l % 5) == 0 || (l % 7) == 0)
                .toArray(), buffer.copyData());
    }

    @Test
    public void testLoadMeta() {
        long[] data = new long[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };
        threesRange().getMetadata(data);
        System.out.println(Arrays.toString(data));

    }
}