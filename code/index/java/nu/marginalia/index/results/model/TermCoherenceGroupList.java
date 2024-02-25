package nu.marginalia.index.results.model;

import nu.marginalia.index.model.SearchTermsUtil;
import nu.marginalia.model.idx.WordMetadata;

import java.util.Collections;
import java.util.List;

/**
 * wordIds that we require to be in the same sentence
 */
public record TermCoherenceGroupList(List<TermCoherenceGroup> words) {

    public TermCoherenceGroupList(List<TermCoherenceGroup> words) {
        this.words = Collections.unmodifiableList(words);
    }

    public boolean test(TermMetadataForCombinedDocumentIds documents, long docId) {
        for (var coherenceSet : words()) {
            if (!coherenceSet.test(documents, docId)) {
                return false;
            }
        }

        return true;
    }

    public static final class TermCoherenceGroup {
        private final long[] words;

        public TermCoherenceGroup(long[] words) {
            this.words = words;
        }

        public TermCoherenceGroup(List<String> coh) {
            this(coh.stream().mapToLong(SearchTermsUtil::getWordId).toArray());
        }

        public boolean test(TermMetadataForCombinedDocumentIds documents, long docId) {
            long overlap = 0xFF_FFFF_FFFF_FFFFL;

            for (var word : words) {
                overlap &= documents.getTermMetadata(word, docId);
            }

            return WordMetadata.decodePositions(overlap) != 0L;
        }
    }
}
