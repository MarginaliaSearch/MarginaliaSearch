package nu.marginalia.index.reverse;

import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.model.idx.WordFlags;

public class ReverseIndexPriorityParameters {
    private static final long highPriorityFlags =
            WordFlags.Title.asBit()
            | WordFlags.Subjects.asBit()
            | WordFlags.TfIdfHigh.asBit()
            | WordFlags.NamesWords.asBit()
            | WordFlags.UrlDomain.asBit()
            | WordFlags.UrlPath.asBit()
            | WordFlags.Site.asBit()
            | WordFlags.SiteAdjacent.asBit();

    public static boolean filterPriorityRecord(IndexJournalEntryData.Record record) {
        long meta = record.metadata();

        return (meta & highPriorityFlags) != 0;
    }


}
