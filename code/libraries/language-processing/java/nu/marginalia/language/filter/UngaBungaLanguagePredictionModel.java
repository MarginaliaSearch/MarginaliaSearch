package nu.marginalia.language.filter;

import nu.marginalia.language.model.DocumentLanguageData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** A simple language prediction model that uses a dictionary of English words
 *  and requires that a certain fraction of the words in the document present in that
 *  dictionary for the document to be considered English.
 *  */
public class UngaBungaLanguagePredictionModel implements LanguagePredictionModel {
    private static final Set<String> englishWords = new HashSet<>();

    public UngaBungaLanguagePredictionModel() {
        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("dictionary/en-1000"),
                "Could not load word frequency table");
             var br = new BufferedReader(new InputStreamReader(resource))
        ) {
            for (;;) {
                String s = br.readLine();
                if (s == null) {
                    break;
                }
                englishWords.add(s.toLowerCase());
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    @Override
    public double predictEnglish(DocumentLanguageData dld) {
        Set<String> seenWords = new HashSet<>();
        int englishCount = 0;

        for (var sent : dld) {
            for (var word : sent.wordsLowerCase) {
                if (seenWords.add(word) && englishWords.contains(word)) {
                    englishCount++;
                }
            }
        }

        return englishCount / (double) Math.min(seenWords.size(), englishWords.size());
    }

    @Override
    public boolean hasPoorAccuracy() {
        return true;
    }
}
