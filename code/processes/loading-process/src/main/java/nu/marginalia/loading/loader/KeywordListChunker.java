package nu.marginalia.loading.loader;


import nu.marginalia.keyword_extraction.model.DocumentKeywords;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeywordListChunker {

    /** Chops data into a list of lists of max length size
     *
     * Caveat: Relies on subList and does not clone "data", so
     * changes to the original list may affect the sub-lists
     * in unspecified ways
     *
     * @see List#subList
     */
    public static List<DocumentKeywords> chopList(DocumentKeywords data, int size) {
        if (data.isEmpty())
            return Collections.emptyList();
        else if (data.size() < size)
            return List.of(data);

        final List<DocumentKeywords> ret = new ArrayList<>(1 + data.size() / size);

        for (int i = 0; i < data.size(); i+=size) {
            ret.add(data.subList(i, Math.min(data.size(), i+size)));
        }

        return ret;
    }
}
