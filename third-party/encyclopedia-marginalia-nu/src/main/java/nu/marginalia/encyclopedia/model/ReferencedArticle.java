package nu.marginalia.encyclopedia.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ReferencedArticle(String title,
                                List<String> aliases,
                                String url,
                                String summary) implements Comparable<ReferencedArticle> {
    public ReferencedArticle(String title, String url, String summary) {
        this(title, List.of(), url, summary);
    }

    public ReferencedArticle withAliases(List<String> aliases) {
        if (aliases != null && aliases.size() > 1) {
            var cleanAliases = new ArrayList<>(aliases);
            cleanAliases.remove(title());
            return new ReferencedArticle(title(), cleanAliases, url(), summary());
        }

        return this;
    }

    private String compareKey() {
        return url.toLowerCase();
    }
    @Override
    public int compareTo(@NotNull ReferencedArticle referencedArticle) {
        return compareKey().compareTo(referencedArticle.compareKey());
    }
}
