package nu.marginalia.converting.processor.plugin;

import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.converting.language.LanguageFilter;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.EdgeUrl;

import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.util.*;

public abstract class AbstractDocumentProcessorPlugin {
    protected LanguageFilter languageFilter;
    public AbstractDocumentProcessorPlugin(LanguageFilter languageFilter) {
        this.languageFilter = languageFilter;
    }

    public abstract DetailsWithWords createDetails(CrawledDocument crawledDocument) throws DisqualifiedException, URISyntaxException;
    public abstract boolean isApplicable(CrawledDocument doc);

    protected void checkDocumentLanguage(DocumentLanguageData dld) throws DisqualifiedException {
        double languageAgreement = languageFilter.dictionaryAgreement(dld);
        if (languageAgreement < 0.1) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LANGUAGE);
        }
    }

    protected static class MetaTagsBuilder {
        private final Set<String> tagWords = new HashSet<>();

        public Set<String> build() {
            return tagWords;
        }

        private void add(String key, @Nullable Object value) {
            if (value == null) {
                return;
            }

            tagWords.add(key + ":" + value.toString().toLowerCase());
        }

        public MetaTagsBuilder addUrl(EdgeUrl url) {
            add("proto", url.proto);
            add("site", url.domain);
            add("site", url.domain.domain);
            add("tld", url.domain.getTld());

            return this;
        }

        public MetaTagsBuilder addGenerator(List<String> generators) {

            for (var generator : generators) {
                add("generator", generator);
            }

            return this;
        }

        public MetaTagsBuilder addFormat(HtmlStandard standard) {

            add("format", standard);

            return this;
        }

        public MetaTagsBuilder addFeatures(Set<HtmlFeature> features) {
            features.stream().map(HtmlFeature::getKeyword).forEach(tagWords::add);

            add("js", features.contains(HtmlFeature.JS));

            return this;
        }
        public MetaTagsBuilder addPubDate(PubDate pubDate) {

            if (pubDate.year() > 1900) {
                add("year", pubDate.year());
            }
            if (pubDate.dateIso8601() != null) {
                add("pub", pubDate.dateIso8601());
            }

            return this;
        }

    }


    public record DetailsWithWords(ProcessedDocumentDetails details,
                                          DocumentKeywordsBuilder words) {}
}
