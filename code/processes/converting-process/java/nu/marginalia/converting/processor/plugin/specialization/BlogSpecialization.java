package nu.marginalia.converting.processor.plugin.specialization;

import ca.rmen.porterstemmer.PorterStemmer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.idx.WordFlags;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeVisitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The blog specialization is used for blogs, and makes heavy assumptions about the nature of the document
 * that aren't generally true, but if the categorization is correct, will yield much better results.
 */
@Singleton
public class BlogSpecialization extends DefaultSpecialization {

    @Inject
    public BlogSpecialization(SummaryExtractor summaryExtractor) {
        super(summaryExtractor);
    }

    @Override
    public Document prune(Document original) {
        var doc = original.clone();

        // Remove all nav junk, comments and other stuff
        doc.filter(new BlogPruningFilter());

        // If there is an article tag, use that as the root
        var articleTags = doc.getElementsByTag("article");
        var firstArticle = articleTags.first();
        if (firstArticle != null) {
            var art = firstArticle.clone();

            doc.body().empty();
            doc.body().appendChild(art);

            return doc;
        }

        // Use the default pruning as a fallback
        return super.prune(doc);
    }

    @Override
    public String getSummary(Document original, Set<String> importantWords) {
        return super.getSummary(original, importantWords);
    }

    private final static List<String> badPathElements =
            List.of("/tag/", "/tags/", "/tagged/", "/category/", "/categories/", "/section/", "/sections/", "/page/", "/author/");

    private final static Predicate<String> dateIndexTest1 = Pattern.compile("^/(\\d{4}/(\\d{2}/){0,2}?)$").asMatchPredicate();
    private final static Predicate<String> dateIndexTest2 = Pattern.compile("^/(\\d{2}/){1,2}$").asMatchPredicate();

    @Override
    public boolean shouldIndex(EdgeUrl url) {
        String path = url.path;

        // Don't index the root path for blogs, as it is usually an ephemeral list of all posts
        if ("/".equals(path)) return false;

        // Likewise for the blog's home page
        if (path.endsWith("/blog/")) return false;
        if (path.endsWith("/log/")) return false;
        if (path.endsWith("/weblog/")) return false;
        if (path.endsWith("/posts/")) return false;
        if (path.endsWith("/articles/")) return false;

        // Refuse paths that contain any of the bad path elements
        for (String badPathElement : badPathElements) {
            if (path.contains(badPathElement)) return false;
        }

        // We don't want chronological listings
        if (dateIndexTest1.test(path)) return false;
        if (dateIndexTest2.test(path)) return false;

        return true;
    }

    private static PorterStemmer ps = new PorterStemmer();
    public void amendWords(Document doc, DocumentKeywordsBuilder words) {
        var tagExtractor = new BlogTagExtractor();
        doc.traverse(tagExtractor);

        var tags = tagExtractor.getTags();
        if (!tags.isEmpty()) {
            var stemmed = tags.stream().map(ps::stemWord).collect(Collectors.toSet());
            words.setFlagOnMetadataForWords(WordFlags.Subjects, stemmed);

            Set<String> specialTags = tags.stream().map(s -> "tag:" + s).collect(Collectors.toSet());
            words.addAllSyntheticTerms(specialTags);
        }

    }

    /** Removes all the non-content elements from the document,
     * making strong blog-specific assumptions about the nature of
     * the layout */
    private static class BlogPruningFilter implements NodeFilter {
        private static final List<String> badClassElements = Arrays.asList("comment", "reply", "sidebar", "header", "footer", "nav");
        private static final List<String> badIdElements = Arrays.asList("comments", "header", "footer", "nav");

        @Override
        public FilterResult head(Node node, int depth) {
            if (node instanceof Element el) {
                String classes = el.attr("class");
                String id = el.id();

                String tagName = el.tagName();

                if (tagName.equalsIgnoreCase("noscript"))
                    return FilterResult.REMOVE;

                for (String badClassElement : badClassElements) {
                    if (classes.contains(badClassElement)) {
                        return FilterResult.REMOVE;
                    }
                }
                for (String badIdElement : badIdElements) {
                    if (id.contains(badIdElement)) {
                        return FilterResult.REMOVE;
                    }
                }
            }
            return FilterResult.CONTINUE;
        }
    }


    // Extract tag keywords from the blog post
    public static class BlogTagExtractor implements NodeVisitor {
        private final Set<String> tags = new HashSet<>();
        int lookForTags = -1;

        public Set<String> getTags() {
            Set<String> tagsClean = tags.stream().map(String::toLowerCase).map(this::cleanTag).filter(Strings::isNotBlank).collect(Collectors.toSet());

            // If there are more than 5 tags, it's probably a global tag listing
            // and not a post-specific tag listing
            if (tagsClean.size() > 5)
                return Set.of();

            return tagsClean;
        }

        private final Pattern splitterPattern = Pattern.compile("\\s+");
        private final Pattern noisePattern = Pattern.compile("[^a-zA-Z0-9]");

        // This is hideously expensive but blog posts are relatively few and far between
        private String cleanTag(String tag) {

            String[] parts = splitterPattern.split(tag);

            if (parts.length > 3)
                return "";

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].startsWith("#"))
                    parts[i] = parts[i].substring(1);
                else if (parts[i].startsWith("(") && parts[i].endsWith(")"))
                    parts[i] = "";
                else
                    parts[i] = noisePattern.matcher(parts[i]).replaceAll("");

                if (parts[i].equals("tags"))
                    parts[i] = "";
            }


            return Arrays.stream(parts).filter(Strings::isNotBlank).collect(Collectors.joining("_"));
        }

        @Override
        public void head(Node node, int depth) {

            if (!(node instanceof Element el)) {
                return;
            }

            if (lookForTags < 0) {
                if (el.attr("class").contains("tags")) {
                    lookForTags = depth;
                }
                if (el.tagName().equals("a")) {
                  if (el.attr("class").contains("tag")
                      || el.attr("href").startsWith("/tag/"))
                     tags.add(el.text());
                }
            }
            else if (el.tagName().equals("a")) {
                    tags.add(el.text());
            }

        }
        public void tail(Node node, int depth) {
            if (depth <= lookForTags) { lookForTags = -1; }
        }
    }
}
