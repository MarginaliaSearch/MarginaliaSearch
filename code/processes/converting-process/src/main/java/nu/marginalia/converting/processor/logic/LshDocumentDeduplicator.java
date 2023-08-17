package nu.marginalia.converting.processor.logic;

import com.google.inject.Singleton;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.lsh.EasyLSH;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Deduplicates documents based on their LSH
 *
 * @see EasyLSH
 */
@Singleton
public class LshDocumentDeduplicator {

    private final int DISTANCE_THRESHOLD = 2;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void deduplicate(List<ProcessedDocument> documents) {
        ProcessedDocument[] goodDocuments = documents.stream()
                .filter(ProcessedDocument::isProcessedFully)
                .filter(doc -> doc.words.size() > 100)
                .toArray(ProcessedDocument[]::new);

        long[] hashCodes = new long[goodDocuments.length];
        for (int i = 0; i < goodDocuments.length; i++) {
            hashCodes[i] = goodDocuments[i].details.hashCode;
        }

        // These arrays can be fairly large (~10,000) so we need to be
        // careful about what we do in this O(n^2) loop

        for (int i = 0; i < hashCodes.length; i++) {
            for (int j = 0; j < hashCodes.length; j++) {
                // This is basically just a 64 bit XOR and a POPCOUNT so it's pretty fast.
                if (EasyLSH.hammingDistance(hashCodes[i], hashCodes[j]) < DISTANCE_THRESHOLD) {
                    if (i == j)
                        continue;

                    if (flagIfDuplicate(goodDocuments[i], goodDocuments[j])) {
                        break;
                    }
                }
            }
        }
    }

    private boolean flagIfDuplicate(ProcessedDocument thisDoc, ProcessedDocument otherDoc) {

        // This document has already been disqualified as a duplicate
        if (thisDoc.state != UrlIndexingState.OK)
            return false;


        // We might consider using thisDoc.details.metadata.topology() here instead of the
        // URL length to determine which document is the "better" one.
        if (thisDoc.url.path.length()
                < otherDoc.url.path.length())
        {
            logger.debug("{} duplicates {}", otherDoc.url, thisDoc.url);

            otherDoc.state = UrlIndexingState.DISQUALIFIED;
            otherDoc.stateReason = "Duplicate";

            return true;
        }

        return false;

    }
}
