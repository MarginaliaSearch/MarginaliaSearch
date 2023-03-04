package nu.marginalia.index.reverse;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.model.crawl.EdgePageWordFlags;

public class ReverseIndexPriorityParameters {
    private static final long highPriorityFlags = EdgePageWordFlags.Title.asBit()
            | EdgePageWordFlags.Subjects.asBit()
            | EdgePageWordFlags.TfIdfHigh.asBit()
            | EdgePageWordFlags.NamesWords.asBit()
            | EdgePageWordFlags.Site.asBit()
            | EdgePageWordFlags.SiteAdjacent.asBit();

    public static boolean filterPriorityRecord(IndexJournalEntryData.Record record) {
        long meta = record.metadata();

        return (meta & highPriorityFlags) != 0;
    }


}
