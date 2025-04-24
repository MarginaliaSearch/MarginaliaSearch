package nu.marginalia.assistant.suggest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class Suggestions {
    List<PrefixSearchStructure> searchStructures = new ArrayList<>();

    private volatile boolean ready = false;

    private static final Logger logger = LoggerFactory.getLogger(Suggestions.class);

    private static final int MIN_SUGGEST_LENGTH = 3;
    @Inject
    public Suggestions(@Named("suggestions-file1") Path suggestionsFile1,
                       @Named("suggestions-file2") Path suggestionsFile2
                       ) {

        Thread.ofPlatform().start(() -> {
            searchStructures.add(loadSuggestions(suggestionsFile1));
            searchStructures.add(loadSuggestions(suggestionsFile2));
            ready = true;
            logger.info("Loaded suggestions");
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
                String line = scanner.nextLine().trim();
                String[] parts = StringUtils.split(line, " ,", 2);
                if (parts.length != 2) {
                    logger.warn("Invalid suggestion line: {}", line);
                    continue;
                }
                int cnt = Integer.parseInt(parts[0]);
                if (cnt > 1) {
                    String word = parts[1];

                    // Remove quotes and trailing periods if this is a CSV
                    if (word.startsWith("\"") && word.endsWith("\"")) {
                        word = word.substring(1, word.length() - 1);
                    }

                    // Remove trailing periods
                    while (word.endsWith(".")) {
                        word = word.substring(0, word.length() - 1);
                    }

                    // Remove junk items we may have gotten from link extraction
                    if (word.startsWith("click here"))
                        continue;

                    if (word.length() > 3) {
                        ret.insert(word, cnt);
                    }
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

        List<PrefixSearchStructure.ScoredSuggestion> resultsAll = new ArrayList<>();

        for (var searchStructure : searchStructures) {
            resultsAll.addAll(searchStructure.getTopCompletions(prefix, count));
        }
        resultsAll.sort(Comparator.reverseOrder());
        List<String> ret = new ArrayList<>(count);

        Set<String> seen = new HashSet<>();
        for (var result : resultsAll) {
            if (seen.add(result.getWord())) {
                ret.add(result.getWord());
            }
            if (ret.size() >= count) {
                break;
            }
        }

        return ret;
    }

}
