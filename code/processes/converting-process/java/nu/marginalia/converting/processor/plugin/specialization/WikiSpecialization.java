package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.summary.SummaryExtractor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;
import java.util.Set;

@Singleton
public class WikiSpecialization extends DefaultSpecialization {

    @Inject
    public WikiSpecialization(SummaryExtractor summaryExtractor) {
        super(summaryExtractor);
    }

    @Override
    public Document prune(Document original) {
        var doc = original.clone();

        // Remove known junk that is common to most mediawikis

        Optional.ofNullable(doc.getElementById("toc")).ifPresent(Element::remove);
        doc.getElementsByTag("table").remove();
        doc.getElementsByTag("aside").remove();
        doc.getElementsByTag("iframe").remove();
        doc.getElementsByTag("noscript").remove();
        doc.getElementsByTag("figure").remove();
        doc.getElementsByClass("wikia-gallery").remove();

        var mainTag = doc.getElementById("mw-content-text");
        // If there is a main tag, we can use that as the root
        // and get good results

        if (mainTag != null) {
            mainTag = mainTag.clone();
            doc.body().empty();
            doc.body().appendChild(mainTag);
            return doc;
        }

        // Use the default pruning as a fallback
        return super.prune(doc);
    }

    @Override
    public String getSummary(Document original, Set<String> importantWords) {
        // Trust wikis to generate a useful summary
        var ogDescription = original.select("meta[property=og:description]").attr("content");
        if (!ogDescription.isBlank()) {
            return ogDescription;
        }

        return super.getSummary(original, importantWords);
    }

    @Override
    public boolean shouldIndex(EdgeUrl url) {
        // Don't index MediaWiki's abundance of special pages
        // -- focus on the articles instead

        if (url.path.contains("Special:")) {
            return false;
        }
        if (url.path.contains("Talk:")) {
            return false;
        }
        if (url.path.contains("User:")) {
            return false;
        }
        if (url.path.contains("User_talk:")) {
            return false;
        }
        if (url.path.contains("File:")) {
            return false;
        }
        if (url.path.contains("Help:")) {
            return false;
        }
        if (url.path.contains(":About")) {
            return false;
        }
        if (url.path.contains("index.php")) {
            return false;
        }

        return true;
    }

    public void amendWords(Document doc, DocumentKeywordsBuilder words) {
    }
}
