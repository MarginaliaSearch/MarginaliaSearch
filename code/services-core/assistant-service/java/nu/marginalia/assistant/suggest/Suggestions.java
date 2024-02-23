package nu.marginalia.assistant.suggest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.functions.math.dict.SpellChecker;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.model.crawl.HtmlFeature;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Suggestions {
    private PatriciaTrie<String> suggestionsTrie = null;
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
            suggestionsTrie = loadSuggestions(suggestionsFile);
            termFrequencyDict = dict;
            ready = true;
            logger.info("Loaded {} suggestions", suggestionsTrie.size());
        });
    }

    private static PatriciaTrie<String> loadSuggestions(Path file) {
        if (!Files.exists(file)) {
            logger.error("Suggestions file {} absent, loading empty suggestions db", file);
            return new PatriciaTrie<>();
        }
        try (var lines = Files.lines(file)) {
            var ret = new PatriciaTrie<String>();

            lines.filter(suggestionPattern.asPredicate())
                    .filter(line -> line.length()<32)
                    .map(String::toLowerCase)
                    .forEach(w -> ret.put(w, w));

            // Add special keywords to the suggestions
            for (var feature : HtmlFeature.values()) {
                String keyword = feature.getKeyword();

                ret.put(keyword, keyword);
                ret.put("-" + keyword, "-" + keyword);
            }

            return ret;
        }
        catch (IOException ex) {
            logger.error("Failed to load suggestions file", ex);
            return new PatriciaTrie<>();
        }
    }

    public List<String> getSuggestions(int count, String searchWord) {
        if (!ready)
            return Collections.emptyList();

        if (searchWord.length() < MIN_SUGGEST_LENGTH) {
            return Collections.emptyList();
        }

        searchWord = StringUtils.stripStart(searchWord.toLowerCase(), " ");

        return Stream.of(
                    new SuggestionStream("", getSuggestionsForKeyword(count, searchWord)),
                    suggestionsForLastWord(count, searchWord),
                    spellCheckStream(searchWord)
                )
                .flatMap(SuggestionsStreamable::stream)
                .limit(count)
                .collect(Collectors.toList());
    }

    private SuggestionsStreamable suggestionsForLastWord(int count, String searchWord) {
        int sp = searchWord.lastIndexOf(' ');

        if (sp < 0) {
            return Stream::empty;
        }

        String prefixString = searchWord.substring(0, sp+1);
        String suggestString = searchWord.substring(sp+1);

        return new SuggestionStream(prefixString, getSuggestionsForKeyword(count, suggestString));

    }

    private SuggestionsStreamable spellCheckStream(String word) {
        int start = word.lastIndexOf(' ');
        String prefix;
        String corrWord;

        if (start < 0) {
            corrWord = word;
            prefix = "";
        }
        else {
            prefix = word.substring(0, start + 1);
            corrWord = word.substring(start + 1);
        }

        if (corrWord.length() >= MIN_SUGGEST_LENGTH) {
            Supplier<Stream<String>> suggestionsLazyEval = () -> spellChecker.correct(corrWord).stream();
            return new SuggestionStream(prefix, Stream.of(suggestionsLazyEval).flatMap(Supplier::get));
        }
        else {
            return Stream::empty;
        }
    }


    public Stream<String> getSuggestionsForKeyword(int count, String prefix) {
        if (!ready)
            return Stream.empty();

        if (prefix.length() < MIN_SUGGEST_LENGTH) {
            return Stream.empty();
        }

        var start = suggestionsTrie.select(prefix);

        if (start == null) {
            return Stream.empty();
        }

        if (!start.getKey().startsWith(prefix)) {
            return Stream.empty();
        }

        SuggestionsValueCalculator sv = new SuggestionsValueCalculator();

        return Stream.iterate(start.getKey(), Objects::nonNull, suggestionsTrie::nextKey)
                .takeWhile(s -> s.startsWith(prefix))
                .limit(256)
                .sorted(Comparator.comparing(sv::get).thenComparing(String::length).thenComparing(Comparator.naturalOrder()))
                .limit(count);
    }

    private record SuggestionStream(String prefix, Stream<String> suggestionStream) implements SuggestionsStreamable {
        public Stream<String> stream() {
            return suggestionStream.map(s -> prefix + s);
        }
    }

    interface SuggestionsStreamable { Stream<String> stream(); }

    private class SuggestionsValueCalculator {

        private final Map<String, Long> hashCache = new HashMap<>(512);

        public int get(String s) {
            long hash = hashCache.computeIfAbsent(s, TermFrequencyDict::getStringHash);
            return -termFrequencyDict.getTermFreqHash(hash);
        }
    }
}
