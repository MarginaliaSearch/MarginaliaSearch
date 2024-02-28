package nu.marginalia.language.sentence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SentencePreCleaner {
    private static final Pattern splitPattern = Pattern.compile("( -|- |\\|)");
    private final int maxSentenceCount = 250;
    private final int maxTotalLength = 20 * maxSentenceCount;

    public String[] clean(String[] sentences) {

        int totalLength = 0;
        int sentenceCount = 0;

        List<String> sentenceList = new ArrayList<>();
        for (var s : sentences) {

            if (s.isBlank()) continue;

            totalLength+=s.length();
            sentenceCount++;

            if (totalLength > maxTotalLength && sentenceCount++ > maxSentenceCount) {
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
