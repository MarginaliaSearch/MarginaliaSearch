package nu.marginalia.wmsa.edge.index.model;

import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDate;

import java.util.EnumSet;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;

public record EdgePageDocumentsMetadata(int rank,
                                        int encSize,
                                        int topology,
                                        int year,
                                        int sets,
                                        int quality,
                                        byte flags) {


    public static final long RANK_MASK = 0xFFL;
    public static final int RANK_SHIFT = 48;

    public static final long ENCSIZE_MASK = 0xFFL;
    public static final int ENCSIZE_SHIFT = 40;
    public static final int ENCSIZE_MULTIPLIER = 50;

    public static final long TOPOLOGY_MASK = 0xFFL;

    public static final int TOPOLOGY_SHIFT = 32;

    public static final long YEAR_MASK = 0xFFL;
    public static final int YEAR_SHIFT = 24;

    public static final long SETS_MASK = 0xFL;
    public static final int SETS_SHIFT = 16;

    public static final long QUALITY_MASK = 0xFL;
    public static final int QUALITY_SHIFT = 8;

    public static long defaultValue() {
        return 0L;
    }
    public EdgePageDocumentsMetadata() {
        this(defaultValue());
    }
    public EdgePageDocumentsMetadata(int topology, int year, int sets, int quality, EnumSet<EdgePageDocumentFlags> flags) {
        this(0, 0, topology, year, sets, quality, encodeFlags(flags));
    }

    public EdgePageDocumentsMetadata withSize(int size) {
        if (size <= 0) {
            return this;
        }

        final int encSize = (int) Math.min(ENCSIZE_MASK, Math.max(1, size / ENCSIZE_MULTIPLIER));

        return new EdgePageDocumentsMetadata(rank, encSize, topology, year, sets, quality, flags);
    }

    private static byte encodeFlags(Set<EdgePageDocumentFlags> flags) {
        byte ret = 0;
        for (var flag : flags) { ret |= flag.asBit(); }
        return ret;
    }

    public boolean hasFlag(EdgePageDocumentFlags flag) {
        return (flags & flag.asBit()) != 0;
    }

    public EdgePageDocumentsMetadata(long value) {
        this(   (int) ((value >>> RANK_SHIFT) & RANK_MASK),
                (int) ((value >>> ENCSIZE_SHIFT) & ENCSIZE_MASK),
                (int) ((value >>> TOPOLOGY_SHIFT) & TOPOLOGY_MASK),
                (int) ((value >>> YEAR_SHIFT) & YEAR_MASK),
                (int) ((value >>> SETS_SHIFT) & SETS_MASK),
                (int) ((value >>> QUALITY_SHIFT) & QUALITY_MASK),
                (byte) (value & 0xFF)
        );
    }

    public static boolean hasFlags(long encoded, long metadataBitMask) {
        return (encoded & metadataBitMask) == encoded;
    }

    public long encode() {
        long ret = 0;
        ret |= Byte.toUnsignedLong(flags);
        ret |= min(QUALITY_MASK, max(0, quality)) << QUALITY_SHIFT;
        ret |= min(SETS_MASK, max(0, sets)) << SETS_SHIFT;
        ret |= min(YEAR_MASK, max(0, year)) << YEAR_SHIFT;
        ret |= min(TOPOLOGY_MASK, max(0, topology)) << TOPOLOGY_SHIFT;
        ret |= min(ENCSIZE_MASK, max(0, encSize)) << ENCSIZE_SHIFT;
        ret |= min(RANK_MASK, max(0, rank)) << RANK_SHIFT;

        return ret;
    }

    public boolean isEmpty() {
        return encSize == 0 && topology == 0 && sets == 0 && quality == 0 && year == 0 && flags == 0 && rank == 0;
    }

    public static int decodeQuality(long encoded) {
        return (int) ((encoded >>> QUALITY_SHIFT) & QUALITY_MASK);
    }

    public static long decodeTopology(long encoded) {
        return (int) ((encoded >>> TOPOLOGY_SHIFT) & TOPOLOGY_MASK);
    }
    public static int decodeYear(long encoded) {

        return PubDate.fromYearByte((int) ((encoded >>> YEAR_SHIFT) & YEAR_MASK));
    }

    public int size() {
        return ENCSIZE_MULTIPLIER * encSize;
    }

    public static int decodeSize(long encoded) {
        return ENCSIZE_MULTIPLIER * (int) ((encoded >>> ENCSIZE_SHIFT) & ENCSIZE_MASK);
    }

    public static int decodeRank(long encoded) {
        return  (int) ((encoded >>> RANK_SHIFT) & RANK_MASK);
    }

    public static long encodeRank(long encoded, int rank) {
        return encoded | min(RANK_MASK, max(0, rank)) << RANK_SHIFT;
    }

}
