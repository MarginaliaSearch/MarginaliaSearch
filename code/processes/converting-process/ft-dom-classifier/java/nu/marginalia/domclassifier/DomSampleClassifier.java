package nu.marginalia.domclassifier;

import com.github.luben.zstd.ZstdInputStream;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.api.domsample.RpcDomainSample;
import nu.marginalia.model.EdgeDomain;
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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Singleton
public class DomSampleClassifier {

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

    public Set<DomSampleClassification> classifySample(RpcDomainSample sample) {
        Set<DomSampleClassification> classifications = new HashSet<>();

        // Look at DOM

        EdgeDomain sampleDomain = new EdgeDomain(sample.getDomainName());

        try (var compressedStream = new ZstdInputStream(sample.getHtmlSampleZstd().newInput())) {
            String html = new String(compressedStream.readAllBytes(), StandardCharsets.UTF_8);
            var parsedDoc = Jsoup.parse(html);
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
            logger.warn("Error when parsing DOM HTML sample", ex);
        }

        // Classify outgoing requests
        for (var req : sample.getOutgoingRequestsList()) {
            EdgeUrl url;

            try {
                url = new EdgeUrl(req.getUrl());
            }
            catch (URISyntaxException ex) {
                continue;
            }

            if (!url.domain.hasSameTopDomain(sampleDomain)) {
                classifications.add(DomSampleClassification.THIRD_PARTY_REQUESTS);
            }

            var clazz = classifyRequest(url);
            if (clazz != DomSampleClassification.IGNORE && clazz != DomSampleClassification.UNCLASSIFIED) {
                classifications.add(clazz);
            }
        }

        return classifications;
    }

    public DomSampleClassification classifyRequest(EdgeUrl edgeUrl) {
        StringBuilder pathSb = new StringBuilder(edgeUrl.path);
        if (edgeUrl.param != null) {
            pathSb.append("?").append(edgeUrl.param);
        }
        String pathMatchString = pathSb.toString();
        String urlDisplayString = edgeUrl.toDisplayString();

        for (Map.Entry<Predicate<String>, DomSampleClassification> regexMatcher : regexClassification) {
            var matcher =  regexMatcher.getKey();
            if (matcher.test(pathMatchString) || matcher.test(urlDisplayString)) {
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

        return DomSampleClassification.UNCLASSIFIED;
    }

}
