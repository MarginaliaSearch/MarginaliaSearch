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

    public boolean test(TermMetadataForCombinedDocumentIds documents, long combinedId) {
        for (var coherenceSet : words()) {
            if (!coherenceSet.test(documents, combinedId)) {
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

        public boolean test(TermMetadataForCombinedDocumentIds documents, long combinedId) {
            long overlap = 0xFF_FFFF_FFFF_FFFFL;

            for (var word : words) {
                long meta = documents.getTermMetadata(word, combinedId);

                // if the word is not present in the document, we omit it from the coherence check
                if (meta != 0L) {
                    overlap &= meta;
                }
            }

            return WordMetadata.decodePositions(overlap) != 0L;
        }
    }
}
