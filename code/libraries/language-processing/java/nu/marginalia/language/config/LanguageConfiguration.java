package nu.marginalia.language.config;

import com.google.inject.Inject;
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

public class LanguageConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(LanguageConfiguration.class);

    private final Map<String, LanguageDefinition> languages = new HashMap<>();

    @Inject
    public LanguageConfiguration() throws IOException, ParserConfigurationException, SAXException {
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
            Element classifier = (Element) classifierNodes.item(i);

            String isoCode = classifier.getAttribute("isoCode").toLowerCase();
            String name = classifier.getAttribute("name");
            boolean disabled = "TRUE".equalsIgnoreCase(classifier.getAttribute("disabled"));

            if (disabled) continue;

            languages.put(isoCode, new LanguageDefinition(isoCode, name));
        }
    }

    @Nullable
    public LanguageDefinition getLanguage(String language) {
        return languages.get(language);
    }

    public record LanguageDefinition(String isoCode, String name) {}
}
