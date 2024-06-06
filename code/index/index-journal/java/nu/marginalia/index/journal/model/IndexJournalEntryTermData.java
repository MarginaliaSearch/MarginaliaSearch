package nu.marginalia.index.journal.model;

import nu.marginalia.sequence.GammaCodedSequence;

/** Data corresponding to a term in a document in the index journal.
 *
 * @param termId the id of the term
 * @param metadata the metadata of the term
 * @param positions the positions of the word in the document, gamma coded
 *
 * @see GammaCodedSequence
 */
public record IndexJournalEntryTermData(
        long termId,
        long metadata,
        GammaCodedSequence positions)
{


}
