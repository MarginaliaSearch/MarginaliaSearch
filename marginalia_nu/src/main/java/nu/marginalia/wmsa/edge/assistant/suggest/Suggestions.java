package nu.marginalia.wmsa.edge.assistant.suggest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.assistant.dict.SpellChecker;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Suggestions {
    private final PatriciaTrie<String> suggestionsTrie;
    private final NGramDict nGramDict;
    private final SpellChecker spellChecker;

    private static final Pattern suggestionPattern = Pattern.compile("^[a-zA-Z0-9]+( [a-zA-Z0-9]+)*$");
    private static final Logger logger = LoggerFactory.getLogger(Suggestions.class);

    private static final int MIN_SUGGEST_LENGTH = 3;
    @Inject
    public Suggestions(@Named("suggestions-file") Path suggestionsFile,
                       SpellChecker spellChecker,
                       NGramDict dict
                       ) {
        this.spellChecker = spellChecker;

        suggestionsTrie = loadSuggestions(suggestionsFile);
        nGramDict = dict;

        logger.info("Loaded {} suggestions", suggestionsTrie.size());
    }

    private static PatriciaTrie<String> loadSuggestions(Path file) {
        try (var lines = Files.lines(file)) {
            var ret = new PatriciaTrie<String>();

            lines.filter(suggestionPattern.asPredicate())
                    .filter(line -> line.length()<32)
                    .map(String::toLowerCase)
                    .forEach(w -> ret.put(w, w));

            return ret;
        }
        catch (IOException ex) {
            logger.error("Failed to load suggestions file", ex);
            return new PatriciaTrie<String>();
        }
    }

    private record SuggestionStream(String prefix, Stream<String> suggestionStream) {
        public Stream<String> stream() {
            return suggestionStream.map(s -> prefix + s);
        }

    }

    public List<String> getSuggestions(int count, String searchWord) {
        if (searchWord.length() < MIN_SUGGEST_LENGTH) {
            return Collections.emptyList();
        }

        searchWord = trimLeading(searchWord.toLowerCase());

        List<SuggestionStream> streams = new ArrayList<>(4);
        streams.add(new SuggestionStream("", getSuggestionsForKeyword(count, searchWord)));

        int sp = searchWord.lastIndexOf(' ');
        if (sp >= 0) {
            String prefixString =  searchWord.substring(0, sp+1);
            String suggestString = searchWord.substring(sp+1);

            if (suggestString.length() >= MIN_SUGGEST_LENGTH) {
                streams.add(new SuggestionStream(prefixString, getSuggestionsForKeyword(count, suggestString)));
            }

        }
        streams.add(spellCheckStream(searchWord));

        return streams.stream().flatMap(SuggestionStream::stream).limit(count).collect(Collectors.toList());
    }

    private SuggestionStream spellCheckStream(String word) {
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
            return new SuggestionStream("", Stream.empty());
        }
    }

    private String trimLeading(String word) {

        for (int i = 0; i < word.length(); i++) {
            if (!Character.isWhitespace(word.charAt(i)))
                return word.substring(i);
        }

        return "";
    }

    public Stream<String> getSuggestionsForKeyword(int count, String prefix) {
        var start = suggestionsTrie.select(prefix);

        if (!start.getKey().startsWith(prefix)) {
            return Stream.empty();
        }

        Map<String, Long> scach = new HashMap<>(512);
        Function<String, Long> valr = s -> -nGramDict.getTermFreqHash(scach.computeIfAbsent(s, NGramDict::getStringHash));

        return Stream.iterate(start.getKey(), Objects::nonNull, suggestionsTrie::nextKey)
                .takeWhile(s -> s.startsWith(prefix))
                .limit(256)
                .sorted(Comparator.comparing(valr).thenComparing(String::length).thenComparing(Comparator.naturalOrder()))
                .limit(count);
    }

}
