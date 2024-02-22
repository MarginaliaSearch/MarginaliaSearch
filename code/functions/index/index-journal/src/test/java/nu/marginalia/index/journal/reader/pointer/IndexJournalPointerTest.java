package nu.marginalia.index.journal.reader.pointer;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexJournalPointerTest {

    @Test
    public void concatenate() {
        MockPointer left = new MockPointer(
                List.of(new MockDocument(1,  2, 3, List.of(
                                new MockRecord(4, 5),
                                new MockRecord(6, 7))
                ))
        );

        MockPointer right = new MockPointer(
                List.of(new MockDocument(8,  9, 10, List.of(
                        new MockRecord(11, 12),
                        new MockRecord(13, 14))
                ))
        );

        IndexJournalPointer concatenated = IndexJournalPointer.concatenate(left, right);
        List<Long> docIdsSeq = new ArrayList<>();
        List<Long> wordIdsSeq = new ArrayList<>();
        while (concatenated.nextDocument()) {
            docIdsSeq.add(concatenated.documentId());
            while (concatenated.nextRecord()) {
                wordIdsSeq.add(concatenated.wordId());
            }
        }

        assertEquals(docIdsSeq, List.of(1L, 8L));
        assertEquals(wordIdsSeq, List.of(4L, 6L, 11L, 13L));
    }

    @Test
    public void filter() {
        MockPointer left = new MockPointer(
                List.of(new MockDocument(1,  2, 3, List.of(
                        new MockRecord(1, 1),
                        new MockRecord(2, 2),
                        new MockRecord(3, 3),
                        new MockRecord(4, 4),
                        new MockRecord(5, 5)
                        )
                ), new MockDocument(2,  2, 3, List.of(
                        new MockRecord(1, 1),
                        new MockRecord(3, 3),
                        new MockRecord(5, 5)
                        )
                ))

        );
        var filtered = left.filterWordMeta(meta -> (meta % 2) == 0);

        List<Long> docIdsSeq = new ArrayList<>();
        List<Long> wordIdsSeq = new ArrayList<>();
        while (filtered.nextDocument()) {
            docIdsSeq.add(filtered.documentId());
            while (filtered.nextRecord()) {
                wordIdsSeq.add(filtered.wordId());
            }
        }

        assertEquals(docIdsSeq, List.of(1L, 2L));
        assertEquals(wordIdsSeq, List.of(2L, 4L));
    }

    class MockPointer implements IndexJournalPointer {
        private final List<MockDocument> documents;

        int di = -1;
        int ri;

        public MockPointer(Collection<MockDocument> documents) {
            this.documents = new ArrayList<>(documents);
        }

        @Override
        public boolean nextDocument() {
            if (++di < documents.size()) {
                ri = -1;
                return true;
            }

            return false;
        }

        @Override
        public boolean nextRecord() {
            if (++ri < documents.get(di).records.size()) {
                return true;
            }

            return false;
        }

        @Override
        public long documentId() {
            return documents.get(di).docId;
        }

        @Override
        public long documentMeta() {
            return documents.get(di).docMeta;
        }

        @Override
        public long wordId() {
            return documents.get(di).records.get(ri).wordId;
        }

        @Override
        public long wordMeta() {
            return documents.get(di).records.get(ri).wordMeta;
        }

        @Override
        public int documentFeatures() {
            return documents.get(di).docFeatures;
        }
    }

    record MockDocument(long docId, long docMeta, int docFeatures, List<MockRecord> records) {}
    record MockRecord(long wordId, long wordMeta) {}
}