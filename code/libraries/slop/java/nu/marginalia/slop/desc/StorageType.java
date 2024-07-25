package nu.marginalia.slop.desc;

/** The type of storage used for a column. */
public enum StorageType {

    /** The column is stored as an uncompressed binary file. */
    PLAIN("bin"),
    /** The column is stored as a compressed binary file using the GZIP algorithm. */
    GZIP("gz"),
    /** The column is stored as a compressed binary file using the ZSTD algorithm. */
    ZSTD("zstd"),
    ;

    public String nmnemonic;

    StorageType(String nmnemonic) {
        this.nmnemonic = nmnemonic;
    }

    public static StorageType fromString(String nmnemonic) {
        for (StorageType type : values()) {
            if (type.nmnemonic.equals(nmnemonic)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown storage type: " + nmnemonic);
    }
}
