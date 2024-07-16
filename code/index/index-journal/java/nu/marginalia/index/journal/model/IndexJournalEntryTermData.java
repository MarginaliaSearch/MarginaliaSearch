package nu.marginalia.index.journal.model;

import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.GammaCodedSequence;

import java.nio.ByteBuffer;

/** Data corresponding to a term in a document in the index journal.
 *
 * @param termId the id of the term
 * @param metadata the metadata of the term
 * @param positionsBuffer buffer holding positions of the word in the document, gamma coded
 *
 * @see GammaCodedSequence
 */
public record IndexJournalEntryTermData(
        long termId,
        long metadata,
        ByteBuffer positionsBuffer)
{
    public CodedSequence positions() {
        return new GammaCodedSequence(positionsBuffer);
    }

}
