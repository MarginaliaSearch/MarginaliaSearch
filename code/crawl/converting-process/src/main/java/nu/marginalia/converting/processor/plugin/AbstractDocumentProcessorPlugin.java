package nu.marginalia.converting.processor.plugin;

import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.language.LanguageFilter;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.crawling.common.model.HtmlStandard;
import nu.marginalia.converting.model.DocumentKeywordsBuilder;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.EdgeUrl;

import java.net.URISyntaxException;
import java.util.*;

public abstract class AbstractDocumentProcessorPlugin {
    protected LanguageFilter languageFilter = new LanguageFilter();

    public abstract DetailsWithWords createDetails(CrawledDomain crawledDomain, CrawledDocument crawledDocument) throws DisqualifiedException, URISyntaxException;
    public abstract boolean isApplicable(CrawledDocument doc);

    protected void checkDocumentLanguage(DocumentLanguageData dld) throws DisqualifiedException {
        double languageAgreement = languageFilter.dictionaryAgreement(dld);
        if (languageAgreement < 0.1) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LANGUAGE);
        }
    }

    protected static class MetaTagsBuilder {
        private final Set<String> tagWords = new HashSet<>();

        public void build(DocumentKeywordsBuilder dest) {
            dest.addAllSyntheticTerms(tagWords);
        }

        public MetaTagsBuilder addDomainCrawlData(CrawledDomain domain) {
            if (domain.ip != null) {
                tagWords.add("ip:" + domain.ip.toLowerCase()); // lower case because IPv6 is hexadecimal
            }
            return this;
        }

        public MetaTagsBuilder addUrl(EdgeUrl url) {
            tagWords.add("proto:"+url.proto.toLowerCase());

            var edgeDomain = url.domain;

            tagWords.add("site:" + edgeDomain.toString().toLowerCase());
            if (!Objects.equals(edgeDomain.toString(), edgeDomain.domain)) {
                tagWords.add("site:" + edgeDomain.domain.toLowerCase());
            }

            tagWords.add("tld:" + edgeDomain.getTld());
            return this;
        }

        public MetaTagsBuilder addFormat(HtmlStandard standard) {
            tagWords.add("format:"+standard.toString().toLowerCase());
            return this;
        }

        public MetaTagsBuilder addFeatures(Set<HtmlFeature> features) {
            features.stream().map(HtmlFeature::getKeyword).forEach(tagWords::add);

            tagWords.add("js:" + Boolean.toString(features.contains(HtmlFeature.JS)).toLowerCase());

            return this;
        }
        public MetaTagsBuilder addPubDate(PubDate pubDate) {

            if (pubDate.year() > 1900) {
                tagWords.add("year:" + pubDate.year());
            }
            if (pubDate.dateIso8601() != null) {
                tagWords.add("pub:" + pubDate.dateIso8601());
            }

            return this;
        }

    }


    public record DetailsWithWords(ProcessedDocumentDetails details,
                                          DocumentKeywordsBuilder words) {}
}
