package nu.marginalia.index.journal.model;

import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.id.UrlIdCodec;
import nu.marginalia.model.idx.DocumentMetadata;

/** The header of an index journal entry.
 *
 * @param entrySize the size of the entry
 * @param documentFeatures the features of the document, as an encoded HtmlFeature
 * @param combinedId the combined document id, encoded with UrlIdCodec
 * @param documentMeta the metadata of the document, as an encoded DocumentMetadata
 *
 * @see DocumentMetadata
 * @see HtmlFeature
 * @see UrlIdCodec
 */
public record IndexJournalEntryHeader(int entrySize,
                                      int documentFeatures,
                                      int documentSize,
                                      long combinedId,
                                      long documentMeta) {

    public IndexJournalEntryHeader(long combinedId,
                                   int documentFeatures,
                                   int documentSize,
                                   long documentMeta) {
        this(-1,
                documentFeatures,
                documentSize,
                combinedId,
                documentMeta);
    }

}
