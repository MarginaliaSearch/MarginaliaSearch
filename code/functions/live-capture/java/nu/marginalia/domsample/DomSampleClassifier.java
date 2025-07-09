package nu.marginalia.domsample;

import nu.marginalia.domsample.db.DomSampleDb;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DomSampleClassifier {
    public enum Classification {
        ADS,
        TRACKING,
        CONSENT,
        POPOVER,
        IGNORE
    }

    private static final Logger logger = LoggerFactory.getLogger(DomSampleClassifier.class);

    private final List<Map.Entry<Predicate<String>, Classification>> regexClassification = new ArrayList<>();
    private final Map<String, Classification> urlClassification = new HashMap<>();
    private final Map<String, Classification> topDomainClassification = new HashMap<>();
    private final Map<String, Classification> fullDomainClassification = new HashMap<>();

    public DomSampleClassifier(InputStream specificationXmlData) throws ParserConfigurationException, IOException, SAXException {
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
            Classification classification = Classification.valueOf(rule.toUpperCase());

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

    public Set<Classification> classify(DomSampleDb.Sample sample) {
        Set<Classification> classifications = new HashSet<>();

        // Look at DOM

        try {
            var parsedDoc = Jsoup.parse(sample.sample());
            var fixedElements = parsedDoc.select("*[data-position=fixed]");

            if (sample.acceptedPopover()) {
                classifications.add(Classification.POPOVER);
            }
            else if (!fixedElements.isEmpty()) {
                String fixedText = fixedElements.text().toLowerCase();
                if (fixedText.contains("cookie") ||
                    fixedText.contains("subscribe") ||
                    fixedText.contains("consent") ||
                    fixedText.contains("newsletter") ||
                    fixedText.contains("gdpr"))
                {
                    classifications.add(Classification.POPOVER);
                }
            }
        }
        catch (Exception ex) {
            logger.warn("Error when parsing DOM HTML sample");
        }

        // Classify outgoing requests
        outer:
        for (var req : sample.parseRequests()) {
            try {
                EdgeUrl edgeUrl = new EdgeUrl(req.uri());

                for (Map.Entry<Predicate<String>, Classification> regexMatcher : regexClassification) {
                    if (regexMatcher.getKey().test(edgeUrl.toDisplayString())) {
                        var clazz = regexMatcher.getValue();

                        if (clazz != Classification.IGNORE) {
                            classifications.add(clazz);
                        }
                        continue outer;
                    }
                }

                Classification clazz = urlClassification.get(edgeUrl.toDisplayString());

                if (clazz != null) {
                    if (clazz != Classification.IGNORE) {
                        classifications.add(clazz);
                    }
                    continue;
                }

                clazz = fullDomainClassification.get(edgeUrl.domain.toString());

                if (clazz != null) {
                    if (clazz != Classification.IGNORE) {
                        classifications.add(clazz);
                    }
                    continue;
                }

                clazz = topDomainClassification.get(edgeUrl.domain.topDomain);

                if (clazz != null) {
                    if (clazz != Classification.IGNORE) {
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
