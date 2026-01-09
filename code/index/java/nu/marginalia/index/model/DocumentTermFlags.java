package nu.marginalia.index.model;

import java.util.Arrays;

public class DocumentTermFlags {
    private byte[] flags;
    public final int nDocs;
    public final int nTerms;

    public DocumentTermFlags(int nTerms, int nDocs) {
        this.nTerms = nTerms;
        this.nDocs = nDocs;

        flags = new byte[nTerms * nDocs];
    }

    public DocumentTermFlags(long[][] valuesForTerm, int nDocs) {
        this.nTerms = valuesForTerm.length;
        this.nDocs = nDocs;

        flags = new byte[nTerms * nDocs];

        for (int termId = 0; termId < nTerms; termId++) {
            if (valuesForTerm[termId] == null)
                continue;
            for (int docId = 0; docId < nDocs; docId++) {
                flags[docId * nTerms + termId] = (byte) (valuesForTerm[termId][docId] & 0xFFL);
            }
        }
    }

    public byte get(int docIdx, int termId) {
        return flags[docIdx * nTerms + termId];
    }

    public long[] get(int docIdx) {
        long[] ret = new long[nTerms];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = flags[docIdx * nTerms + i];
        }
        return ret;
    }
}
