package nu.marginalia.language.config;

import com.github.jfasttext.JFastText;
import com.google.inject.Inject;
import nu.marginalia.LanguageModels;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.stemming.Stemmer;
import org.jsoup.nodes.TextNode;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LanguageConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(LanguageConfiguration.class);

    private final Map<String, LanguageDefinition> languages = new HashMap<>();
    private final JFastText fastTextLanguageModel = new JFastText();

    @Inject
    public LanguageConfiguration(LanguageModels lm) throws IOException, ParserConfigurationException, SAXException {
        fastTextLanguageModel.loadModel(lm.fasttextLanguageModel.toString());

        // TODO: Read from data directory

        try (var languagesXmlStream = ClassLoader.getSystemResourceAsStream("languages.xml")) {
            if (languagesXmlStream == null)
                throw new IllegalStateException("languages.xml resource not found in classpath");
            loadConfiguration(languagesXmlStream);
        }

        logger.info("Loaded language configuration: {}", languages);
    }

    private void loadConfiguration(InputStream xmlData) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlData);

        NodeList classifierNodes = doc.getElementsByTagName("language");

        for (int i = 0; i < classifierNodes.getLength(); i++) {
            Element languageTag = (Element) classifierNodes.item(i);

            boolean disabled = "TRUE".equalsIgnoreCase(languageTag.getAttribute("disabled"));
            if (disabled) continue;

            String isoCode = languageTag.getAttribute("isoCode").toLowerCase();
            String name = languageTag.getAttribute("name");

            Stemmer stemmer = parseStemmerTag(languageTag, isoCode);

            languages.put(isoCode, new LanguageDefinition(isoCode, name, stemmer));
        }
    }

    private Stemmer parseStemmerTag(Element languageElement, String isoCode) {
        NodeList stemmerElements = languageElement.getElementsByTagName("stemmer");
        if (stemmerElements.getLength() != 1) {
            throw new IllegalArgumentException("language.xml: No stemmer block for language element " + isoCode);
        }
        Element stemmerElement = (Element) stemmerElements.item(0);

        String stemmerName = stemmerElement.getAttribute("algorithm");
        String stemmerVariant = stemmerElement.getTextContent().trim();

        return switch (stemmerName.toLowerCase()) {
            case "porter" -> new Stemmer.Porter();
            case "snowball" -> new Stemmer.Snowball(stemmerVariant);
            case "none" -> new Stemmer.NoOpStemmer();
            default -> throw new  IllegalArgumentException("language.xml: Unknown stemmer name " + stemmerName + " in " + isoCode);
        };
    }

    public Optional<LanguageDefinition> identifyLanguage(org.jsoup.nodes.Document jsoupDoc) {
        StringBuilder sampleBuilder = new StringBuilder();
        jsoupDoc.body().traverse((node, depth) -> {
            if (sampleBuilder.length() > 4096) return;
            if (!(node instanceof TextNode tn)) return;

            sampleBuilder.append(' ').append(tn.text());
        });
        return identifyLanguage(sampleBuilder.toString());
    }

    public Optional<LanguageDefinition> identifyLanguage(String sample) {
        String prediction = fastTextLanguageModel.predict(sample);

        if (prediction.length() == "__label__??".length()) {
            String isoCode = prediction.substring("__label__".length());
            return Optional.ofNullable(getLanguage(isoCode));
        }

        return Optional.empty();
    }
    public Optional<LanguageDefinition> identifyLanguage(String sample, String fallbackIsoCode) {
        return identifyLanguage(sample).or(() -> Optional.ofNullable(getLanguage(fallbackIsoCode)));
    }

    @Nullable
    public LanguageDefinition getLanguage(String language) {
        return languages.get(language);
    }

}
