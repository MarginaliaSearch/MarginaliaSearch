package nu.marginalia.language.sentence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SentencePreCleaner {
    private static final Pattern splitPattern = Pattern.compile("( -|- |\\|)");

    public String[] clean(String[] sentences) {

        int sentenceCount = 0;

        List<String> sentenceList = new ArrayList<>();
        for (var s : sentences) {

            if (s.isBlank()) continue;

            sentenceCount++;

            if (sentenceCount++ > SentenceExtractor.MAX_SENTENCE_COUNT) {
                break;
            }

            if (s.contains("-") || s.contains("|")) {
                sentenceList.addAll(Arrays.asList(splitPattern.split(s)));
            }
            else {
                sentenceList.add(s);
            }
        }

        return sentenceList.toArray(String[]::new);
    }
}
