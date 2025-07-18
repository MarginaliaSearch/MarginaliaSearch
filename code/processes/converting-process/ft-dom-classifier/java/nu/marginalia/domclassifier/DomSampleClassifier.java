package nu.marginalia.domclassifier;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.domsample.RpcDomainSample;
import nu.marginalia.api.domsample.RpcOutgoingRequest;
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

@Singleton
public class DomSampleClassifier {

    /** Feature classifications for the DOM sample */
    public enum DomSampleClassification {
        ADS(HtmlFeature.ADVERTISEMENT),
        TRACKING(HtmlFeature.TRACKING_ADTECH),
        CONSENT(HtmlFeature.CONSENT),
        POPOVER(HtmlFeature.POPOVER),
        UNCLASSIFIED(HtmlFeature.MISSING_DOM_SAMPLE),
        IGNORE(null);

        @Nullable
        public final HtmlFeature htmlFeature;

        DomSampleClassification(@Nullable HtmlFeature feature) {
            this.htmlFeature = feature;
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
                    regexClassification.add(Map.entry(Pattern.compile(content).asPredicate(), classification));
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

    public DomSampleClassification classifyRequest(RpcOutgoingRequest request) {
        try {
            EdgeUrl edgeUrl = new EdgeUrl(request.getUrl());

            for (Map.Entry<Predicate<String>, DomSampleClassification> regexMatcher : regexClassification) {
                if (regexMatcher.getKey().test(edgeUrl.toDisplayString())) {
                    var clazz = regexMatcher.getValue();

                    if (clazz != DomSampleClassification.IGNORE) {
                        return clazz;
                    }
                }
            }

            DomSampleClassification clazz = urlClassification.get(edgeUrl.toDisplayString());

            if (clazz != null && clazz != DomSampleClassification.IGNORE) {
                return clazz;
            }

            clazz = fullDomainClassification.get(edgeUrl.domain.toString());

            if (clazz != null && clazz != DomSampleClassification.IGNORE) {
                return clazz;
            }

            clazz = topDomainClassification.get(edgeUrl.domain.topDomain);

            if (clazz != null && clazz != DomSampleClassification.IGNORE) {
                return clazz;
            }
        }
        catch (Exception e) {
            // Ignore parsing errors
        }

        return DomSampleClassification.UNCLASSIFIED;
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
        for (var req : sample.getOutgoingRequestsList()) {
            var clazz = classifyRequest(req);
            if (clazz != DomSampleClassification.IGNORE && clazz != DomSampleClassification.UNCLASSIFIED) {
                classifications.add(clazz);
            }
        }

        return classifications;
    }
}
