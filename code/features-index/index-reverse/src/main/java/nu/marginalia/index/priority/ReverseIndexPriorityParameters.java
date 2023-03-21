package nu.marginalia.index.priority;

import nu.marginalia.btree.model.BTreeBlockSize;
import nu.marginalia.btree.model.BTreeContext;
import nu.marginalia.index.journal.model.IndexJournalEntryData;
import nu.marginalia.model.idx.WordFlags;

public class ReverseIndexPriorityParameters {
    static final int ENTRY_SIZE = 1;
    static final BTreeBlockSize blockSize = BTreeBlockSize.BS_4096;

    static final BTreeContext bTreeContext = new BTreeContext(5, ENTRY_SIZE, blockSize);

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
