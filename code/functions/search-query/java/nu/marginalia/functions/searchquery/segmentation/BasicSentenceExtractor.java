package nu.marginalia.functions.searchquery.segmentation;

import ca.rmen.porterstemmer.PorterStemmer;
import org.apache.commons.lang3.StringUtils;

public class BasicSentenceExtractor {

    private static PorterStemmer porterStemmer = new PorterStemmer();
    public static String[] getStemmedParts(String sentence) {
        String[] parts = StringUtils.split(sentence, ' ');
        for (int i = 0; i < parts.length; i++) {
            parts[i] = porterStemmer.stemWord(parts[i]);
        }
        return parts;
    }
}
