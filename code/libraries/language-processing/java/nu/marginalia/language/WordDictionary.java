package nu.marginalia.language;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class WordDictionary {
    private final Set<String> words;
    private static final Logger logger = LoggerFactory.getLogger(WordDictionary.class);

    private WordDictionary(Set<String> words) {
        this.words = words;
    }

    public static WordDictionary fromClasspathResource(String resourceName) {
        var set = new HashSet<String>(200, 0.5f);

        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(resourceName),
                "Could not load word frequency table");
             var br = new BufferedReader(new InputStreamReader(resource))
        ) {
            while (true) {
                String s = br.readLine();

                if (s == null) break;
                if (s.isBlank()) continue;

                set.add(s.trim());
            }
        } catch (IOException e) {
            logger.warn("Failed to load resource " + resourceName, e);
        }

        return new WordDictionary(set);
    }

    public boolean contains(String str) {
        return words.contains(str);
    }
}
