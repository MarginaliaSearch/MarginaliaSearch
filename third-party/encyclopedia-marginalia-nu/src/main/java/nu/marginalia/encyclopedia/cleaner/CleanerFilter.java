package nu.marginalia.encyclopedia.cleaner;

import lombok.Builder;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;

import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Builder
public class CleanerFilter implements NodeFilter {
    final Set<String> badTags;
    final Set<String> badIds;
    final Set<String> badClasses;

    final Set<Predicate<Element>> predicates;

    private static final Pattern spacePattern = Pattern.compile("\\s+");

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
                }
                else if (badClasses.contains(className)) {
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
}
