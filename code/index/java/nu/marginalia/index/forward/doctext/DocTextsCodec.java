package nu.marginalia.index.forward.doctext;

/** Encodes offset and size of a compressed document text in the document texts file
 * into a single long, which is stored in the forward index docs data file.
 */
public class DocTextsCodec {
    public static final int MAX_BLOB_SIZE = (1 << 24) - 1;
    private static final long MAX_OFFSET = (1L << 40) - 1;

    public static long encode(long startOffset, int size) {
        assert startOffset >= 0 && startOffset <= MAX_OFFSET : "Offset out of range: " + startOffset;
        assert size > 0 && size <= MAX_BLOB_SIZE : "Size out of range: " + size;

        return startOffset << 24 | size;
    }

    public static long encodeAbsent() {
        return 0;
    }

    public static boolean isAbsent(long encoded) {
        return decodeSize(encoded) == 0;
    }

    public static long decodeStartOffset(long encoded) {
        return encoded >>> 24;
    }

    public static int decodeSize(long encoded) {
        return (int) (encoded & MAX_BLOB_SIZE);
    }
}
