package nu.marginalia.index.journal.model;

import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.sequence.CodedSequence;

public record IndexJournalEntryData(long[] termIds,
                                    long[] metadata,
                                    CodedSequence[] positions) {

    public IndexJournalEntryData {
        assert termIds.length == metadata.length;
        assert termIds.length == positions.length;
    }

    public IndexJournalEntryData(String[] keywords,
                                 long[] metadata,
                                 CodedSequence[] positions)
    {
        this(termIds(keywords), metadata, positions);
    }

    private static final MurmurHash3_128 hash = new MurmurHash3_128();

    public int size() {
        return termIds.length;
    }


    private static long[] termIds(String[] keywords) {
        long[] termIds = new long[keywords.length];
        for (int i = 0; i < keywords.length; i++) {
            termIds[i] = hash.hashKeyword(keywords[i]);
        }
        return termIds;
    }
}
