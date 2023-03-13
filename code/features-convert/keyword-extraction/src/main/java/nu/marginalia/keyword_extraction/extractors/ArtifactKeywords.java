package nu.marginalia.keyword_extraction.extractors;

import nu.marginalia.language.model.DocumentLanguageData;

import java.util.*;
import java.util.regex.Pattern;

public class ArtifactKeywords {

    private static final Pattern mailLikePattern = Pattern.compile("[a-zA-Z0-9._\\-]+@[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+");

    private final Set<String> words;

    public ArtifactKeywords(DocumentLanguageData documentLanguageData) {
        words = new HashSet<>();

        for (var sent : documentLanguageData.sentences) {
            for (var word : sent) {
                String lc = word.wordLowerCase();
                if (lc.length() < 6
                        || lc.indexOf('@') < 0
                        || !mailLikePattern.matcher(lc).matches()) {
                    continue;
                }

                words.add(lc);

                String domain = lc.substring(lc.indexOf('@'));
                String user = lc.substring(0, lc.indexOf('@'));

                if (!domain.equals("@hotmail.com") && !domain.equals("@gmail.com")  && !domain.equals("@paypal.com")) {
                    words.add(domain.substring(1));
                    words.add(domain);
                }
                if (!user.equals("info") && !user.equals("legal") && !user.equals("contact") && !user.equals("donotreply")) {
                    words.add(user);
                }

            }
        }

    }

    public Collection<String> getWords() {
        return words;
    }
}
