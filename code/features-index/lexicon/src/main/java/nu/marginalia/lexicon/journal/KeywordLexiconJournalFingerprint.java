package nu.marginalia.lexicon.journal;

/** Contains values used to assess whether the lexicon is in sync with the journal
 *  or if it has been replaced with a newer version and should be reloaded
 * */
public record KeywordLexiconJournalFingerprint(long createdTime,
                                               long mTime,
                                               long sizeBytes)
{
}
