package nu.marginalia.slop.desc;

/** The type of function that a column performs.
 * This is used to determine how to interpret the
 * data in the column.
 */
public enum ColumnFunction {
    /** The principal data column. */
    DATA("dat"),
    /** The length column for the DATA column, in the case of variable-length records. */
    DATA_LEN("dat-len"),
    /** The length column for the group of items in the DATA column, in the case of variable-length array-style records. */
    GROUP_LENGTH("grp-len"),
    /** The dictionary column, in the case of a dictionary-encoded column. */
    DICT("dic"),
    /** The length column for the DICT column, in the case of variable-length dictionaries. */
    DICT_LEN("dic-len"),
    ;

    public String nmnemonic;

    ColumnFunction(String nmnemonic) {
        this.nmnemonic = nmnemonic;
    }

    /** Return the appropriate column function for
     * a length column corresponding to the current
     * column function.
     */
    public ColumnFunction lengthsTable() {
        switch (this) {
            case DATA:
                return DATA_LEN;
            case DICT:
                return DICT_LEN;
            default:
                throw new IllegalArgumentException("Cannot get length table type for " + this);
        }
    }

    public static ColumnFunction fromString(String nmnemonic) {
        for (ColumnFunction type : values()) {
            if (type.nmnemonic.equals(nmnemonic)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown column function: " + nmnemonic);
    }
}
