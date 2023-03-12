package nu.marginalia.converting.processor.keywords.extractors;

import nu.marginalia.language.model.DocumentLanguageData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ArtifactKeywords {

    private static final Pattern mailLikePattern = Pattern.compile("[a-zA-Z0-9._\\-]+@[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+");

    public List<String> getArtifactKeywords(DocumentLanguageData documentLanguageData) {
        Set<String> reps = new HashSet<>();

        for (var sent : documentLanguageData.sentences) {
            for (var word : sent) {
                String lc = word.wordLowerCase();
                if (lc.length() < 6
                        || lc.indexOf('@') < 0
                        || !mailLikePattern.matcher(lc).matches()) {
                    continue;
                }

                reps.add(lc);

                String domain = lc.substring(lc.indexOf('@'));
                String user = lc.substring(0, lc.indexOf('@'));

                if (!domain.equals("@hotmail.com") && !domain.equals("@gmail.com")  && !domain.equals("@paypal.com")) {
                    reps.add(domain);
                }
                if (!user.equals("info") && !user.equals("legal") && !user.equals("contact") && !user.equals("donotreply")) {
                    reps.add(user);
                }

            }
        }

        return new ArrayList<>(reps);
    }

}
