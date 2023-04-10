package nu.marginalia.keyword.extractors;

import nu.marginalia.language.model.DocumentLanguageData;

import java.util.*;
import java.util.regex.Pattern;

public class ArtifactKeywords {

    private static final Pattern mailLikePattern = Pattern.compile("[a-zA-Z0-9._\\-]+@[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+");

    private static final Set<String> ignoredDomains = Set.of("@hotmail.com", "@gmail.com", "@paypal.com");
    private static final Set<String> ignoredUsers = Set.of("info", "legal", "contact", "press", "donotreply", "noreply", "no-reply", "admin", "root");

    private final Set<String> words = new HashSet<>();

    public ArtifactKeywords(DocumentLanguageData documentLanguageData) {

        for (var sent : documentLanguageData.sentences) {
            for (var word : sent) {
                final String lc = word.wordLowerCase();
                final int atIdx = lc.indexOf('@');

                if (lc.length() < 6 || atIdx < 0 || !mailLikePattern.matcher(lc).matches()) {
                    continue;
                }

                words.add(lc);

                String domain = lc.substring(atIdx);
                String user = lc.substring(0, atIdx);

                if (!ignoredDomains.contains(domain)) {
                    words.add(domain.substring(1));
                    words.add(domain);
                }
                if (!ignoredUsers.contains(user)) {
                    words.add(user);
                }

            }
        }

    }

    public Collection<String> getWords() {
        return words;
    }
}
