package nu.marginalia.converting.language;

import nu.marginalia.language.model.DocumentLanguageData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class UngaBungaLanguagePredictionModel implements LanguagePredictionModel {
    private static final Set<String> englishWords = new HashSet<>();

    public UngaBungaLanguagePredictionModel() throws Exception {
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

        for (var sent : dld.sentences) {
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
