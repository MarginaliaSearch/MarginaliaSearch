package nu.marginalia.model.idx;


import java.util.EnumSet;

public enum WordFlags {
    /** Word appears in title */
    Title,

    /** Word appears to be the subject in several sentences */
    Subjects,

    /** Word is a likely named object. This is a weaker version of Subjects. */
    NamesWords,

    /** The word isn't actually a word on page, but a fake keyword from the code
     * to aid discovery
     */
    Synthetic,

    /** Word is important to site
     */
    Site,

    /** Word is important to adjacent documents
     * */
    SiteAdjacent,

    /** Keyword appears in URL path
     */
    UrlPath,

    /** Keyword appears in domain name
     */
    UrlDomain,

    /** Word appears in an external link */
    ExternalLink
    ;

    public byte asBit() {
        return (byte) (1 << ordinal());
    }

    public boolean isPresent(byte value) {
        return (asBit() & value) > 0;
    }

    public boolean isAbsent(byte value) {
        return (asBit() & value) == 0;
    }

    public static byte encode(EnumSet<WordFlags> flags) {
        byte ret = 0;
        for (WordFlags f : flags) {
            ret |= f.asBit();
        }
        return ret;
    }

    public static EnumSet<WordFlags> decode(byte encodedValue) {
        EnumSet<WordFlags> ret = EnumSet.noneOf(WordFlags.class);

        for (WordFlags f : values()) {
            if ((encodedValue & f.asBit()) > 0) {
                ret.add(f);
            }
        }

        return ret;
    }

}
