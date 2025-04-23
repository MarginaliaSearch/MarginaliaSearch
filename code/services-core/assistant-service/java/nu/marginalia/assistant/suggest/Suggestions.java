package nu.marginalia.assistant.suggest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.functions.math.dict.SpellChecker;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class Suggestions {
    private PrefixSearchStructure searchStructure = null;
    private TermFrequencyDict termFrequencyDict = null;
    private volatile boolean ready = false;
    private final SpellChecker spellChecker;

    private static final Pattern suggestionPattern = Pattern.compile("^[a-zA-Z0-9]+( [a-zA-Z0-9]+)*$");
    private static final Logger logger = LoggerFactory.getLogger(Suggestions.class);

    private static final int MIN_SUGGEST_LENGTH = 3;
    @Inject
    public Suggestions(@Named("suggestions-file") Path suggestionsFile,
                       SpellChecker spellChecker,
                       TermFrequencyDict dict
                       ) {
        this.spellChecker = spellChecker;

        Thread.ofPlatform().start(() -> {
            searchStructure = loadSuggestions(suggestionsFile);
            termFrequencyDict = dict;
            ready = true;
            logger.info("Loaded {} suggestions", searchStructure.size());
        });
    }

    private static PrefixSearchStructure loadSuggestions(Path file) {
        PrefixSearchStructure ret = new PrefixSearchStructure();

        if (!Files.exists(file)) {
            logger.error("Suggestions file {} absent, loading empty suggestions db", file);
            return ret;
        }

        try (var scanner = new Scanner(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ))))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = StringUtils.split(line, " ", 2);
                if (parts.length != 2) {
                    logger.warn("Invalid suggestion line: {}", line);
                    continue;
                }
                int cnt = Integer.parseInt(parts[0]);
                if (cnt > 1) {
                    String word = parts[1];
                    ret.insert(word, cnt);
                }
            }
            return ret;
        }
        catch (IOException ex) {
            logger.error("Failed to load suggestions file", ex);
            return new PrefixSearchStructure();
        }
    }

    public List<String> getSuggestions(int count, String searchWord) {
        if (!ready)
            return Collections.emptyList();

        if (searchWord.length() < MIN_SUGGEST_LENGTH) {
            return Collections.emptyList();
        }

        searchWord = StringUtils.stripStart(searchWord.toLowerCase(), " ");

        return getSuggestionsForKeyword(count, searchWord);
    }

    public List<String> getSuggestionsForKeyword(int count, String prefix) {
        if (!ready)
            return List.of();

        if (prefix.length() < MIN_SUGGEST_LENGTH) {
            return List.of();
        }

        var results = searchStructure.getTopCompletions(prefix, count);
        List<String> ret = new ArrayList<>(count);
        for (var result : results) {
            ret.add(result.getWord());
        }

        return ret;
    }

}
