package nu.marginalia.index.forward;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.SpansCodec;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;

import static nu.marginalia.index.config.ForwardIndexParameters.*;

/** Reads the forward index.
 * <p/>
 * The forward index is constructed of a staggered array
 * called 'data' containing domains and document level metadata,
 * and a mapping between document identifiers to the index into the
 * data array.
 * <p/>
 * The metadata is a binary encoding of {@see nu.marginalia.idx.DocumentMetadata}
 */
public class ForwardIndexReader {
    private final LongArray ids;
    private final LongArray data;

    private volatile Long2IntOpenHashMap idsMap;

    private final DomainRankings domainRankings;

    private final int spansFd;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public ForwardIndexReader(Path idsFile,
                              Path dataFile,
                              Path spansFile) throws IOException {
        if (!Files.exists(dataFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", dataFile);
            ids = null;
            data = null;
            domainRankings = null;
            spansFd = -1;
            return;
        }
        else if (!Files.exists(idsFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", idsFile);
            ids = null;
            data = null;
            domainRankings = null;
            spansFd = -1;
            return;
        }
        else if (!Files.exists(spansFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", spansFile);
            ids = null;
            data = null;
            domainRankings = null;
            spansFd = -1;
            return;
        }

        logger.info("Switching forward index");

        ids = loadIds(idsFile);
        data = loadData(dataFile);

        domainRankings = new DomainRankings();
        domainRankings.load(dataFile.getParent());

        LinuxSystemCalls.madviseRandom(data.getMemorySegment());
        LinuxSystemCalls.madviseRandom(ids.getMemorySegment());

        spansFd = LinuxSystemCalls.openBuffered(spansFile);
        LinuxSystemCalls.fadviseWillneed(spansFd);

        Thread.ofPlatform().start(this::createIdsMap);
    }

    private void createIdsMap() {
        Long2IntOpenHashMap idsMap = new Long2IntOpenHashMap((int) ids.size());
        for (int i = 0; i < ids.size(); i++) {
            idsMap.put(ids.get(i), i);
        }
        this.idsMap = idsMap;
        logger.info("Forward index loaded into RAM");
    }

    private static LongArray loadIds(Path idsFile) throws IOException {
        return LongArrayFactory.mmapForReadingShared(idsFile);
    }

    private static LongArray loadData(Path dataFile) throws IOException {
        return LongArrayFactory.mmapForReadingShared(dataFile);
    }

    /** For a given domain id, return the lowest document id including its encoded rank as seen in the reverse index.
     *
     * This function is needed to help find documents for a particular domain on disk, an operation which is not
     * part of the regular index lookups, but are needed when filtering search results efficiently.
     *
     * When document ids are written to disk, they are prefixed with a rank byte, to affect their sort order.
     * This function encodes a document id with the appropriate rank, domain id provider, and document ordinal zero.
     *
     * If ret is the return value of this function for some domain id, all the documents from that domain will have ids
     * ranging between ret and ret | 0x03FF_FFFF.
     */
    public long getRankEncodedDocumentIdBase(int domainId) {

        // This is a bit awkward since we need to match the exact order of operations used in the index construction logic,
        // where "idWithNoRank" is already provided!
        long idWithNoRank = UrlIdCodec.encodeId(domainId, 0);
        float rank = domainRankings.getSortRanking(idWithNoRank);

        return UrlIdCodec.addRank(rank, idWithNoRank);
    }

    public long getDocMeta(long combinedDocId) {
        long offset = idxForDoc(combinedDocId);
        if (offset < 0) return 0;

        return data.get(ENTRY_SIZE * offset + METADATA_OFFSET);
    }

    public int getHtmlFeatures(long combinedDocId) {
        long offset = idxForDoc(combinedDocId);
        if (offset < 0) return 0;

        return (int) (data.get(ENTRY_SIZE * offset + FEATURES_OFFSET) & 0xFFFF_FFFFL);
    }

    public int getDocumentSize(long combinedDocId) {
        long offset = idxForDoc(combinedDocId);
        if (offset < 0) return 0;

        return (int) (data.get(ENTRY_SIZE * offset + FEATURES_OFFSET) >>> 32L);
    }


    private int idxForDoc(long combinedDocId) {

        final long strippedId = UrlIdCodec.removeRank(combinedDocId);

        if (idsMap != null) {
            int ret = idsMap.getOrDefault(strippedId, -1);

            if (ret == -1) {
                logger.warn("Could not find offset for doc {} ({}:{}:{})", combinedDocId,
                        UrlIdCodec.getRank(combinedDocId),
                        UrlIdCodec.getDomainId(combinedDocId),
                        UrlIdCodec.getDocumentOrdinal(combinedDocId));
            }

            return ret;
        }

        long offset = ids.binarySearch2(strippedId, 0, ids.size());

        if (offset >= ids.size() || offset < 0 || ids.get(offset) != strippedId) {

            logger.warn("Could not find offset for doc {} ({}:{}:{})", combinedDocId,
                    UrlIdCodec.getRank(combinedDocId),
                    UrlIdCodec.getDomainId(combinedDocId),
                    UrlIdCodec.getDocumentOrdinal(combinedDocId));

            return -1;
        }

        return (int) offset;
    }

    @Nullable
    public DecodableDocumentSpans getDocumentSpans(Arena arena, long documentId) {

        long fwdIdxOffset = idxForDoc(documentId);
        if (fwdIdxOffset < 0) {
            return null;
        }

        long encodedOffset = data.get(ENTRY_SIZE * fwdIdxOffset + SPANS_OFFSET);

        long readOffset = SpansCodec.decodeStartOffset(encodedOffset);
        int readSize = SpansCodec.decodeSize(encodedOffset);

        MemorySegment segment = arena.allocate(readSize, 8);

        LinuxSystemCalls.readAt(spansFd, segment, readOffset);

        return new DecodableDocumentSpans(segment);
    }

    public int totalDocCount() {
        return (int) ids.size();
    }

    public void close() {
        if (spansFd >= 0)
            LinuxSystemCalls.closeFd(spansFd);

        if (data != null)
            data.close();
        if (ids != null)
            ids.close();
    }

    public boolean isLoaded() {
        return data != null;
    }
}
