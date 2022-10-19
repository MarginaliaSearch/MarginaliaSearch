package nu.marginalia.wmsa.edge.index.reader;

import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import nu.marginalia.util.btree.BTreeQueryBuffer;
import nu.marginalia.util.btree.BTreeReader;
import nu.marginalia.util.multimap.MultimapFileLong;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.svc.query.types.EmptyEntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySource;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySourceFromBTree;
import nu.marginalia.wmsa.edge.index.svc.query.types.EntrySourceFromMapRange;

import javax.annotation.Nullable;

import static nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags.*;

public class SearchIndexURLRange {
    public final long dataOffset;
    private final MultimapFileLong urlsFile;

    @Nullable
    private final BTreeReader reader;

    public SearchIndexURLRange(MultimapFileLong urlsFile, long dataOffset) {
        this.dataOffset = dataOffset;
        this.urlsFile = urlsFile;

        if (dataOffset >= 0) {
            this.reader = new BTreeReader(urlsFile, SearchIndexConverter.urlsBTreeContext, dataOffset);
        } else {
            this.reader = null;
        }
    }

    public EntrySource asPrefixSource(long prefix, long prefixNext) {
        if (reader == null)
            return new EmptyEntrySource();

        LongLongImmutablePair startAndEnd = reader.getRangeForPrefix(prefix, prefixNext);

        if (startAndEnd.firstLong() == startAndEnd.secondLong()) {
            return new EmptyEntrySource();
        }

        return new EntrySourceFromMapRange(urlsFile, startAndEnd.firstLong(), startAndEnd.secondLong());
    }

    public EntrySource asEntrySource() {
        return new EntrySourceFromBTree(reader, EntrySourceFromBTree.NO_MASKING, null);
    }
    public EntrySource asQualityLimitingEntrySource(int limit) {
        return new EntrySourceFromBTree(reader, EntrySourceFromBTree.NO_MASKING, limit);
    }
    public EntrySource asDomainEntrySource() {
        return new EntrySourceFromBTree(reader, Subjects.asBit() | Site.asBit() | Title.asBit(), null);
    }

    public boolean isPresent() {
        return dataOffset >= 0;
    }

    public long numEntries() {
        if (reader == null)
            return 0L;

        return reader.numEntries();
    }

    public void retainUrls(BTreeQueryBuffer buffer) {
        if (reader != null)
            reader.retainEntries(buffer);
    }

    public void rejectUrls(BTreeQueryBuffer buffer) {
        if (reader != null)
            reader.rejectEntries(buffer);
    }

    public boolean hasUrl(long url) {
        if (reader == null)
            return false;

        return reader.findEntry(url) >= 0;
    }


    public long[] getMetadata(long[] urls) {
        if (reader == null) {
            return new long[urls.length];
        }

        return reader.queryData(urls, 1);
    }

    @Override
    public String toString() {
        return String.format("BTreeRange(@" + dataOffset + ", size = " + numEntries() + ")");
    }

}
