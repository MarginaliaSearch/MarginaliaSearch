package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;
import java.util.Set;

@Singleton
public class WikiSpecialization extends DefaultSpecialization {

    @Inject
    public WikiSpecialization(TitleExtractor titleExtractor) {
        super(titleExtractor);
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

    @Override
    public double lengthModifier() {
        return 2.5;
    }
}
