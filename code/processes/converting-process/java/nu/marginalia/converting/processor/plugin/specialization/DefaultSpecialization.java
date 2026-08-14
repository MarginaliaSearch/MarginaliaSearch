package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.dom.DomPruningFilter;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class DefaultSpecialization implements HtmlProcessorSpecializations.HtmlProcessorSpecializationIf {

    private final TitleExtractor titleExtractor;

    @Inject
    public DefaultSpecialization(TitleExtractor titleExtractor) {
        this.titleExtractor = titleExtractor;
    }

    @Override
    public Document prune(Document doc) {
        final var prunedDoc = doc.clone();

        prunedDoc.body().filter(new DomPruningFilter(0.5));

        return prunedDoc;
    }

    @Override
    public String getTitle(Document original, DocumentLanguageData dld, String url) {
        return titleExtractor.getTitleAbbreviated(original, dld, url);
    }

    public boolean shouldIndex(EdgeUrl url) { return true; }
    public double lengthModifier() { return 1.0; }

    public void amendWords(Document doc, DocumentKeywordsBuilder words) {}
}
