package nu.marginalia.encyclopedia.cleaner;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class CleanerFilter implements NodeFilter {
    final Set<String> badTags;
    final Set<String> badIds;
    final Set<String> badClasses;

    final Set<Predicate<Element>> predicates;

    private static final Pattern spacePattern = Pattern.compile("\\s+");

    CleanerFilter(Set<String> badTags, Set<String> badIds, Set<String> badClasses, Set<Predicate<Element>> predicates) {
        this.badTags = badTags;
        this.badIds = badIds;
        this.badClasses = badClasses;
        this.predicates = predicates;
    }

    public static CleanerFilterBuilder builder() {
        return new CleanerFilterBuilder();
    }

    @Override
    public FilterResult head(Node node, int depth) {
        if (node instanceof Element el) {
            if (badTags != null && badTags.contains(el.tagName()))
                return FilterResult.REMOVE;

            if (badIds != null && badIds.contains(el.id()))
                return FilterResult.REMOVE;

            if (badClasses != null) {
                String className = el.className();
                if (className.contains(" ")) {
                    String[] parts = spacePattern.split(className);
                    for (var c : parts) {
                        if (badClasses.contains(c))
                            return FilterResult.REMOVE;
                    }
                } else if (badClasses.contains(className)) {
                    return FilterResult.REMOVE;
                }
            }

            if (predicates != null) {
                for (var pred : predicates) {
                    if (pred.test(el))
                        return FilterResult.REMOVE;
                }
            }
        }

        if (node instanceof Comment) {
            return FilterResult.REMOVE;
        }

        return FilterResult.CONTINUE;
    }

    public static class CleanerFilterBuilder {
        private Set<String> badTags;
        private Set<String> badIds;
        private Set<String> badClasses;
        private Set<Predicate<Element>> predicates;

        CleanerFilterBuilder() {
        }

        public CleanerFilterBuilder badTags(Set<String> badTags) {
            this.badTags = badTags;
            return this;
        }

        public CleanerFilterBuilder badIds(Set<String> badIds) {
            this.badIds = badIds;
            return this;
        }

        public CleanerFilterBuilder badClasses(Set<String> badClasses) {
            this.badClasses = badClasses;
            return this;
        }

        public CleanerFilterBuilder predicates(Set<Predicate<Element>> predicates) {
            this.predicates = predicates;
            return this;
        }

        public CleanerFilter build() {
            return new CleanerFilter(this.badTags, this.badIds, this.badClasses, this.predicates);
        }

        public String toString() {
            return "CleanerFilter.CleanerFilterBuilder(badTags=" + this.badTags + ", badIds=" + this.badIds + ", badClasses=" + this.badClasses + ", predicates=" + this.predicates + ")";
        }
    }
}
