package nu.marginalia.converting.processor.plugin;

import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.domclassifier.DomSampleClassification;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawldata.CrawledDocument;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractDocumentProcessorPlugin {
    public abstract DetailsWithWords createDetails(
            CrawledDocument crawledDocument,
            LinkTexts linkTexts,
            Set<DomSampleClassification> domSampleClassifications,
            DocumentClass documentClass)
            throws DisqualifiedException, UnsupportedLanguageException,  URISyntaxException, IOException;
    public abstract boolean isApplicable(CrawledDocument doc);

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
            add("site", url.domain.topDomain);
            add("tld", url.domain.getTld());

            if (url.path.startsWith("/~")) {
                add("special", "tilde");
            }

            return this;
        }

        public MetaTagsBuilder addGenerator(List<String> generators) {

            for (var generator : generators) {
                add("generator", generator);
            }

            return this;
        }

        public MetaTagsBuilder addLanguage(String language) {
            /* For now let's hold back on adding a language keyword, since
              language selection is implemented via partitioning in the index
              service already!

              While the index can certainly cope with keywords that hold basically every document
              in the index, this is not free and should be avoided without a good cause.
             */

            // add("lang", language);

            return this;
        }

        public MetaTagsBuilder addFormat(DocumentFormat standard) {

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
