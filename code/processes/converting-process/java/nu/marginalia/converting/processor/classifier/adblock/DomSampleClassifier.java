package nu.marginalia.converting.processor.classifier.adblock;

import com.google.inject.Inject;
import nu.marginalia.api.domsample.RpcDomainSample;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DomSampleClassifier {
    public enum DomSampleClassification {
        ADS(HtmlFeature.ADVERTISEMENT),
        TRACKING(HtmlFeature.TRACKING_ADTECH),
        CONSENT(HtmlFeature.CONSENT),
        POPOVER(HtmlFeature.POPOVER),
        IGNORE(null);

        @Nullable
        public final HtmlFeature htmlFeature;

        DomSampleClassification(@Nullable HtmlFeature feature) {
            this.htmlFeature = feature;
        }

        public void apply(ProcessedDocumentDetails details, DocumentKeywordsBuilder words) {
            if (null == htmlFeature)
                return;

            details.features.add(htmlFeature);
            words.addSyntheticTerm(htmlFeature.getKeyword());
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(DomSampleClassifier.class);

    private final List<Map.Entry<Predicate<String>, DomSampleClassification>> regexClassification = new ArrayList<>();
    private final Map<String, DomSampleClassification> urlClassification = new HashMap<>();
    private final Map<String, DomSampleClassification> topDomainClassification = new HashMap<>();
    private final Map<String, DomSampleClassification> fullDomainClassification = new HashMap<>();

    @Inject
    public DomSampleClassifier() throws ParserConfigurationException, IOException, SAXException {
        this(ClassLoader.getSystemResourceAsStream("request-classifier.xml"));
    }

    public DomSampleClassifier(InputStream specificationXmlData) throws ParserConfigurationException, IOException, SAXException {
        Objects.requireNonNull(specificationXmlData, "specificationXmlData is null");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(specificationXmlData);

        NodeList classifierNodes = doc.getElementsByTagName("classifier");

        for (int i = 0; i < classifierNodes.getLength(); i++) {
            Element classifier = (Element) classifierNodes.item(i);

            String target = classifier.getAttribute("target");
            String rule = classifier.getAttribute("rule");
            String content = classifier.getTextContent().trim();

            // Convert rule to Classification enum
            DomSampleClassification classification = DomSampleClassification.valueOf(rule.toUpperCase());

            // Add to appropriate map based on target
            switch (target) {
                case "url":
                    urlClassification.put(content, classification);
                    break;
                case "url-regex":
                    regexClassification.add(Map.entry(Pattern.compile(content).asMatchPredicate(), classification));
                    break;
                case "top":
                    topDomainClassification.put(content, classification);
                    break;
                case "domain":
                    fullDomainClassification.put(content, classification);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown target type: " + target);
            }
        }

    }

    public Set<DomSampleClassification> classify(RpcDomainSample sample) {
        Set<DomSampleClassification> classifications = new HashSet<>();

        // Look at DOM

        try {
            var parsedDoc = Jsoup.parse(sample.getHtmlSample());
            var fixedElements = parsedDoc.select("*[data-position=fixed]");

            if (sample.getAcceptedPopover()) {
                classifications.add(DomSampleClassification.POPOVER);
            }
            else if (!fixedElements.isEmpty()) {
                String fixedText = fixedElements.text().toLowerCase();
                if (fixedText.contains("cookie") ||
                        fixedText.contains("subscribe") ||
                        fixedText.contains("consent") ||
                        fixedText.contains("newsletter") ||
                        fixedText.contains("gdpr"))
                {
                    classifications.add(DomSampleClassification.POPOVER);
                }
            }
        }
        catch (Exception ex) {
            logger.warn("Error when parsing DOM HTML sample");
        }

        // Classify outgoing requests
        outer:
        for (var req : sample.getOutgoingRequestsList()) {
            try {
                EdgeUrl edgeUrl = new EdgeUrl(req.getUrl());

                for (Map.Entry<Predicate<String>, DomSampleClassification> regexMatcher : regexClassification) {
                    if (regexMatcher.getKey().test(edgeUrl.toDisplayString())) {
                        var clazz = regexMatcher.getValue();

                        if (clazz != DomSampleClassification.IGNORE) {
                            classifications.add(clazz);
                        }
                        continue outer;
                    }
                }

                DomSampleClassification clazz = urlClassification.get(edgeUrl.toDisplayString());

                if (clazz != null) {
                    if (clazz != DomSampleClassification.IGNORE) {
                        classifications.add(clazz);
                    }
                    continue;
                }

                clazz = fullDomainClassification.get(edgeUrl.domain.toString());

                if (clazz != null) {
                    if (clazz != DomSampleClassification.IGNORE) {
                        classifications.add(clazz);
                    }
                    continue;
                }

                clazz = topDomainClassification.get(edgeUrl.domain.topDomain);

                if (clazz != null) {
                    if (clazz != DomSampleClassification.IGNORE) {
                        classifications.add(clazz);
                    }
                    continue;
                }
            }
            catch (Exception e) {
                // Ignore parsing errors
            }
        }

        return classifications;
    }
}
