package nu.marginalia.index.forward;

import com.github.luben.zstd.Zstd;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.ffi.LinuxSystemCalls;
import nu.marginalia.index.config.ForwardIndexParameters;
import nu.marginalia.index.forward.doctext.DocTextsCodec;
import nu.marginalia.index.forward.spans.DecodableDocumentSpans;
import nu.marginalia.index.forward.spans.SpansCodec;
import nu.marginalia.index.model.FeaturesCodec;
import nu.marginalia.ranking.DomainRankings;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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

    private final int dataFd;
    private final int spansFd;

    private final FileChannel docTextsChannel;
    private static final long MAX_TEXT_LENGTH = 1L << 27;

    private final int entrySize;

    /** Mapping of the spans file, for fetch paths that read resident pages
     *  directly instead of copying them through the fds above */
    private final Arena spansArena;
    private final MemorySegment spansSegment;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static ForwardIndexVersion version;

    public ForwardIndexReader(Path idsFile,
                              Path dataFile,
                              Path spansFile,
                              Path docTextsFile) throws IOException {
        if (!Files.exists(dataFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", dataFile);
            ids = null;
            data = null;
            domainRankings = null;
            dataFd = -1;
            spansFd = -1;
            spansArena = null;
            spansSegment = null;
            docTextsChannel = null;
            entrySize = 0;
            version = null;
            return;
        }
        else if (!Files.exists(idsFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", idsFile);
            ids = null;
            data = null;
            domainRankings = null;
            dataFd = -1;
            spansFd = -1;
            spansArena = null;
            spansSegment = null;
            docTextsChannel = null;
            entrySize = 0;
            version = null;
            return;
        }
        else if (!Files.exists(spansFile)) {
            logger.warn("Failed to create ForwardIndexReader, {} is absent", spansFile);
            ids = null;
            data = null;
            domainRankings = null;
            dataFd = -1;
            spansFd = -1;
            spansArena = null;
            spansSegment = null;
            docTextsChannel = null;
            entrySize = 0;
            version = null;
            return;
        }

        ids = loadIds(idsFile);
        data = loadData(dataFile);

        version = ForwardIndexParameters.decodeVersion(data.get(data.size() - 1));
        entrySize = version.entrySize;

        logger.info("Switching forward index, version {}", version);

        if (version.compareTo(ForwardIndexVersion.V2026_08__1) >= 0 && Files.exists(docTextsFile)) {
            docTextsChannel = FileChannel.open(docTextsFile, StandardOpenOption.READ);
        }
        else {
            logger.warn("Document texts are not available, snippets will not be generated");
            docTextsChannel = null;
        }

        domainRankings = new DomainRankings();
        domainRankings.load(dataFile.getParent());

        LinuxSystemCalls.madviseNormal(data.getMemorySegment());
        LinuxSystemCalls.madviseRandom(ids.getMemorySegment());

        dataFd = LinuxSystemCalls.openBuffered(dataFile);
        LinuxSystemCalls.fadviseRandom(dataFd);

        spansFd = LinuxSystemCalls.openBuffered(spansFile);
        LinuxSystemCalls.fadviseWillneed(spansFd);
        LinuxSystemCalls.fadviseRandom(spansFd);

        spansArena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(spansFile, StandardOpenOption.READ)) {
            spansSegment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), spansArena);
        }
        LinuxSystemCalls.madviseNormal(spansSegment);

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

        return data.get(entrySize * offset + METADATA_OFFSET);
    }

    public int getHtmlFeatures(long combinedDocId) {
        long offset = idxForDoc(combinedDocId);
        if (offset < 0) return 0;

        long encoded = data.get(entrySize * offset + FEATURES_OFFSET);
        return FeaturesCodec.getHtmlFeatures(encoded);
    }

    public int getDocumentSize(long combinedDocId) {
        long offset = idxForDoc(combinedDocId);
        if (offset < 0) return 0;

        long encoded = data.get(entrySize * offset + FEATURES_OFFSET);
        return FeaturesCodec.getDocumentSize(encoded, version);
    }

    public int getDocPubDate(long combinedDocId) {
        long offset = idxForDoc(combinedDocId);
        if (offset < 0) return 0;

        long encoded = data.get(entrySize * offset + FEATURES_OFFSET);
        return FeaturesCodec.getPubDate(encoded, version);
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
    public DecodableDocumentSpans getDocumentSpans(SegmentAllocator allocator, long documentId) {

        long fwdIdxOffset = idxForDoc(documentId);
        if (fwdIdxOffset < 0) {
            return null;
        }

        long encodedOffset = data.get(entrySize * fwdIdxOffset + SPANS_OFFSET);

        long readOffset = SpansCodec.decodeStartOffset(encodedOffset);
        int readSize = SpansCodec.decodeSize(encodedOffset);

        MemorySegment segment = allocator.allocate(readSize, 8);

        LinuxSystemCalls.readAt(spansFd, segment, readOffset);

        return new DecodableDocumentSpans(segment);
    }

    /** Returns the document text stored for the given document, or null
     * if no text is stored for it or the stored blob cannot be read */
    @Nullable
    public String getDocumentText(long documentId) {
        if (docTextsChannel == null) {
            return null;
        }

        long fwdIdxOffset = idxForDoc(documentId);
        if (fwdIdxOffset < 0) {
            return null;
        }

        long encodedOffset = data.get(entrySize * fwdIdxOffset + DOC_TEXT_OFFSET);
        if (DocTextsCodec.isAbsent(encodedOffset)) {
            return null;
        }

        long readOffset = DocTextsCodec.decodeStartOffset(encodedOffset);
        int readSize = DocTextsCodec.decodeSize(encodedOffset);

        byte[] blob = new byte[readSize];
        try {
            ByteBuffer buffer = ByteBuffer.wrap(blob);
            while (buffer.hasRemaining()) {
                if (docTextsChannel.read(buffer, readOffset + buffer.position()) < 0)
                    return null;
            }
        }
        catch (IOException ex) {
            logger.error("Failed to read document text", ex);
            return null;
        }

        try {
            long size = Zstd.getFrameContentSize(blob);
            if (size <= 0 || size > MAX_TEXT_LENGTH) {
                logger.warn("Implausible document text size {} for document {}", size, documentId);
                return null;
            }

            return new String(Zstd.decompress(blob, (int) size), StandardCharsets.UTF_8);
        }
        catch (RuntimeException ex) {
            logger.warn("Failed to decompress document text for document {}", documentId, ex);
            return null;
        }
    }

    public int totalDocCount() {
        return (int) ids.size();
    }

    /** True when the in-memory id lookup table has finished building.  Until then
     *  lookups fall back to a binary search over the mmapped ids file. */
    public boolean isIdsMapReady() {
        return idsMap != null;
    }

    /** Byte offset of the document's entry in the data file, or -1 if the
     *  document is not in the index */
    public long dataOffsetForDoc(long combinedDocId) {
        long idx = idxForDoc(combinedDocId);
        if (idx < 0) {
            return -1;
        }
        return 8L * entrySize * idx;
    }

    public int dataFd() {
        return dataFd;
    }

    public int spansFd() {
        return spansFd;
    }

    public MemorySegment mappedData() {
        return data.getMemorySegment();
    }

    public MemorySegment mappedSpans() {
        return spansSegment;
    }

    public ForwardIndexVersion version() {
        return version;
    }

    public void close() {
        try {
            if (dataFd >= 0)
                LinuxSystemCalls.closeFd(dataFd);
        }
        catch (RuntimeException ex) {
            logger.error("Error closing 'dataFd'", ex);
        }

        try {
            if (spansFd >= 0)
                LinuxSystemCalls.closeFd(spansFd);
        }
        catch (RuntimeException ex) {
            logger.error("Error closing 'spansFd'", ex);
        }

        try {
            if (spansArena != null)
                spansArena.close();
        }
        catch (RuntimeException ex) {
            logger.error("Error closing 'spansArena'", ex);
        }

        try {
            if (docTextsChannel != null)
                docTextsChannel.close();
        }
        catch (IOException | RuntimeException ex) {
            logger.error("Error closing 'docTextsChannel'", ex);
        }

        try {
            if (data != null)
                data.close();
        }
        catch (RuntimeException ex) {
            logger.error("Error closing 'data'", ex);
        }

        try {
            if (data != null)
                ids.close();
        }
        catch (RuntimeException ex) {
            logger.error("Error closing 'ids'", ex);
        }
    }

    public boolean isLoaded() {
        return data != null;
    }
}
