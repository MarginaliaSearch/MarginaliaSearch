package nu.marginalia.wmsa.edge.index.postings.reverse;

import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.postings.journal.model.SearchIndexJournalEntry;

public class ReverseIndexPriorityParameters {
    private static final long highPriorityFlags = EdgePageWordFlags.Title.asBit()
            | EdgePageWordFlags.Subjects.asBit()
            | EdgePageWordFlags.TfIdfHigh.asBit()
            | EdgePageWordFlags.NamesWords.asBit()
            | EdgePageWordFlags.Site.asBit()
            | EdgePageWordFlags.SiteAdjacent.asBit();

    public static boolean filterPriorityRecord(SearchIndexJournalEntry.Record record) {
        long meta = record.metadata();

        return (meta & highPriorityFlags) != 0;
    }


}
