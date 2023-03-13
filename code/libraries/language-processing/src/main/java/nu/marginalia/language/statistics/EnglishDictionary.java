package nu.marginalia.language.statistics;

import com.google.inject.Inject;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EnglishDictionary {
    private final Set<String> englishWords = new HashSet<>();
    private final TermFrequencyDict tfDict;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public EnglishDictionary(TermFrequencyDict tfDict) {
        this.tfDict = tfDict;
        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("dictionary/en-words"),
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

    public boolean isWord(String word) {
        return englishWords.contains(word);
    }

    private static final Pattern ingPattern = Pattern.compile(".*(\\w)\\1ing$");

    public Collection<String> getWordVariants(String s) {
        var variants = findWordVariants(s);

        var ret = variants.stream()
                .filter(var -> tfDict.getTermFreq(var) > 100)
                .collect(Collectors.toList());

        if (s.equals("recipe") || s.equals("recipes")) {
            ret.add("category:food");
        }

        return ret;
    }


    public Collection<String> findWordVariants(String s) {
        int sl = s.length();

        if (sl < 2) {
            return Collections.emptyList();
        }
        if (s.endsWith("s")) {
            String a = s.substring(0, sl-1);
            String b = s + "es";
            if (isWord(a) && isWord(b)) {
                return List.of(a, b);
            }
            else if (isWord(a)) {
                return List.of(a);
            }
            else if (isWord(b)) {
                return List.of(b);
            }
        }
        if (s.endsWith("sm")) {
            String a = s.substring(0, sl-1)+"t";
            String b = s.substring(0, sl-1)+"ts";
            if (isWord(a) && isWord(b)) {
                return List.of(a, b);
            }
            else if (isWord(a)) {
                return List.of(a);
            }
            else if (isWord(b)) {
                return List.of(b);
            }
        }
        if (s.endsWith("st")) {
            String a = s.substring(0, sl-1)+"m";
            String b = s + "s";
            if (isWord(a) && isWord(b)) {
                return List.of(a, b);
            }
            else if (isWord(a)) {
                return List.of(a);
            }
            else if (isWord(b)) {
                return List.of(b);
            }
        }
        else if (ingPattern.matcher(s).matches() && sl > 4) { // humming, clapping
            var a = s.substring(0, sl-4);
            var b = s.substring(0, sl-3) + "ed";

            if (isWord(a) && isWord(b)) {
                return List.of(a, b);
            }
            else if (isWord(a)) {
                return List.of(a);
            }
            else if (isWord(b)) {
                return List.of(b);
            }
        }
        else {
            String a = s + "s";
            String b = ingForm(s);
            String c = s + "ed";

            if (isWord(a) && isWord(b) && isWord(c)) {
                return List.of(a, b, c);
            }
            else if (isWord(a) && isWord(b)) {
                return List.of(a, b);
            }
            else if (isWord(b) && isWord(c)) {
                return List.of(b, c);
            }
            else if (isWord(a) && isWord(c)) {
                return List.of(a, c);
            }
            else if (isWord(a)) {
                return List.of(a);
            }
            else if (isWord(b)) {
                return List.of(b);
            }
            else if (isWord(c)) {
                return List.of(c);
            }
        }

        return Collections.emptyList();
    }

    public String ingForm(String s) {
        if (s.endsWith("t") && !s.endsWith("tt")) {
            return s + "ting";
        }
        if (s.endsWith("n") && !s.endsWith("nn")) {
            return s + "ning";
        }
        if (s.endsWith("m") && !s.endsWith("mm")) {
            return s + "ming";
        }
        if (s.endsWith("r") && !s.endsWith("rr")) {
            return s + "ring";
        }
        return s + "ing";
    }
}
