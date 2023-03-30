package nu.marginalia.model.idx;

import nu.marginalia.model.crawl.PubDate;

import java.util.EnumSet;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;

public record DocumentMetadata(int avgSentLength,
                               int rank,
                               int encDomainSize,
                               int topology,
                               int year,
                               int sets,
                               int quality,
                               byte flags) {

    public static final long ASL_MASK = 0x03L;
    public static final int ASL_SHIFT = 56;

    public static final long RANK_MASK = 0xFFL;
    public static final int RANK_SHIFT = 48;

    public static final long ENC_DOMAIN_SIZE_MASK = 0xFFL;
    public static final int ENC_DOMAIN_SIZE_SHIFT = 40;
    public static final int ENC_DOMAIN_SIZE_MULTIPLIER = 5;

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
    public DocumentMetadata() {
        this(defaultValue());
    }

    public DocumentMetadata(int avgSentLength, int year, int sets, int quality, EnumSet<DocumentFlags> flags) {
        this(avgSentLength, 0, 0, 0, year, sets, quality, encodeFlags(flags));
    }

    public DocumentMetadata withSize(int size, int topology) {
        final int encSize = (int) Math.min(ENC_DOMAIN_SIZE_MASK, Math.max(1, size / ENC_DOMAIN_SIZE_MULTIPLIER));

        return new DocumentMetadata(avgSentLength, rank, encSize, topology, year, sets, quality, flags);
    }

    private static byte encodeFlags(Set<DocumentFlags> flags) {
        byte ret = 0;
        for (var flag : flags) { ret |= flag.asBit(); }
        return ret;
    }

    public boolean hasFlag(DocumentFlags flag) {
        return (flags & flag.asBit()) != 0;
    }

    public DocumentMetadata(long value) {
        this(
                (int) ((value >>> ASL_SHIFT) & ASL_MASK),
                (int) ((value >>> RANK_SHIFT) & RANK_MASK),
                (int) ((value >>> ENC_DOMAIN_SIZE_SHIFT) & ENC_DOMAIN_SIZE_MASK),
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
        ret |= min(ENC_DOMAIN_SIZE_MASK, max(0, encDomainSize)) << ENC_DOMAIN_SIZE_SHIFT;
        ret |= min(RANK_MASK, max(0, rank)) << RANK_SHIFT;
        ret |= min(ASL_MASK, max(0, avgSentLength)) << ASL_SHIFT;
        return ret;
    }

    public boolean isEmpty() {
        return avgSentLength == 0 && encDomainSize == 0 && topology == 0 && sets == 0 && quality == 0 && year == 0 && flags == 0 && rank == 0;
    }

    public static int decodeQuality(long encoded) {
        return (int) ((encoded >>> QUALITY_SHIFT) & QUALITY_MASK);
    }

    public static int decodeTopology(long encoded) {
        return (int) ((encoded >>> TOPOLOGY_SHIFT) & TOPOLOGY_MASK);
    }

    public static int decodeAvgSentenceLength(long encoded) {
        return (int) ((encoded >>> ASL_SHIFT) & ASL_MASK);
    }

    public static int decodeYear(long encoded) {
        return PubDate.fromYearByte((int) ((encoded >>> YEAR_SHIFT) & YEAR_MASK));
    }

    public int size() {
        return ENC_DOMAIN_SIZE_MULTIPLIER * encDomainSize;
    }

    public static int decodeSize(long encoded) {
        return ENC_DOMAIN_SIZE_MULTIPLIER * (int) ((encoded >>> ENC_DOMAIN_SIZE_SHIFT) & ENC_DOMAIN_SIZE_MASK);
    }

    public static int decodeRank(long encoded) {
        return  (int) ((encoded >>> RANK_SHIFT) & RANK_MASK);
    }

    public static long encodeRank(long encoded, int rank) {
        return encoded | min(RANK_MASK, max(0, rank)) << RANK_SHIFT;
    }

}
