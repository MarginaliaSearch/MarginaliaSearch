package nu.marginalia.model.id;

/** URL id encoding scheme, including an optional ranking part that's used in the indices and washed away
 * outside.   The ranking part is put in the highest bits so that when we sort the documents by id, they're
 * actually sorted by rank.  Next is the domain id part, which keeps documents from the same domain clustered.
 * Finally is the document ordinal part, which is a non-unique sequence number for within the current set of
 * documents loaded.  The same ID may be re-used over time as a new index is loaded.
 * <p></p>
 * <table>
 *     <tr><th>Part</th><th>Bits</th><th>Cardinality</th></tr>
 *     <tr>
 *         <td>rank</td><td>6 bits</td><td>64</td>
 *     </tr>
 *     <tr>
 *         <td>domain</td><td>31 bits</td><td>2 billion</td>
 *     </tr>
 *     <tr>
 *         <td>document</td><td>26 bits</td><td>67 million</td>
 *     </tr>
 * </table>
 *  <p></p>
 *  Most significant bit is unused for now because I'm not routing Long.compareUnsigned() all over the codebase.
 *  <i>If</i> we end up needing more domains, we'll cross that bridge when we come to it.
 *
 * <h2>Coding Scheme</h2>
 * <code><pre>
 * [    | rank | domain | url ]
 *  0   1       6       38    64
 * </pre></code>
 */
public class UrlIdCodec {
    private static final long RANK_MASK = 0xFE00_0000_0000_0000L;
    private static final int DOCORD_MASK = 0x03FF_FFFF;

    /** Encode a URL id without a ranking element */
    public static long encodeId(int domainId, int documentOrdinal) {
        domainId &= 0x7FFF_FFFF;
        documentOrdinal &= 0x03FF_FFFF;

        return ((long) domainId << 26) | documentOrdinal;
    }

    /** Add a ranking element to an existing combined URL id.
     *
     * @param rank [0,1] the importance of the domain, low is good
     * @param urlId
     */
    public static long addRank(float rank, long urlId) {
        long rankPart = (int)(rank * (1<<6));

        if (rankPart >= 64) rankPart = 63;
        if (rankPart < 0) rankPart = 0;

        return (urlId&(~RANK_MASK)) | (rankPart << 57);
    }

    /** Extract the domain component from this URL id */
    public static int getDomainId(long combinedId) {
        return (int) ((combinedId >>> 26) & 0x7FFF_FFFFL);
    }

    /** Extract the document ordinal component from this URL id */
    public static int getDocumentOrdinal(long combinedId) {
        return (int) (combinedId & DOCORD_MASK);
    }


    /** Extract the document ordinal component from this URL id */
    public static int getRank(long combinedId) {
        return (int) (combinedId >>> 57);
    }

    /** Mask out the ranking element from this URL id */
    public static long removeRank(long combinedId) {
        return combinedId & ~RANK_MASK;
    }

}
