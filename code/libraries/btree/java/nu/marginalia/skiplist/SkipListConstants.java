package nu.marginalia.skiplist;

public class SkipListConstants {
    static final int BLOCK_SIZE = 512;
    static final int HEADER_SIZE = 8;
    static final int RECORD_SIZE = 2;
    static final int MAX_RECORDS_PER_BLOCK = 32;

    static final byte FLAG_END_BLOCK = 1<<0;
}
