package nu.marginalia.model.crawl;


import java.util.EnumSet;

public enum EdgePageWordFlags {

    /** Word appears in title */
    Title,

    /** Word appears to be the subject in several sentences
     * @see SubjectCounter */
    Subjects,

    /** Word has high tf-idf
     * @see KeywordCounter */
    TfIdfHigh,

    /** Word is a likely named object. This is a weaker version of Subjects.
     * @see NameCounter */
    NamesWords,

    /** The word isn't actually a word on page, but a fake keyword from the code
     * to aid discovery
     */
    Synthetic,

    /** Word is important to site
     * @see SiteWords
     */
    Site,

    /** Word is important to adjacent documents
     * @see SiteWords
     * */
    SiteAdjacent;

    public int asBit() {
        return 1 << ordinal();
    }

    public boolean isPresent(long value) {
        return (asBit() & value) > 0;
    }

    public static EnumSet<EdgePageWordFlags> decode(long encodedValue) {
        EnumSet<EdgePageWordFlags> ret = EnumSet.noneOf(EdgePageWordFlags.class);

        for (EdgePageWordFlags f : values()) {
            if ((encodedValue & f.asBit()) > 0) {
                ret.add(f);
            }
        }

        return ret;
    }
}
