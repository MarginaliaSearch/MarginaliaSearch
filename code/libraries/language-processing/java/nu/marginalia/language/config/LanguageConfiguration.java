package nu.marginalia.language.config;

import com.github.jfasttext.JFastText;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.LanguageModels;
import nu.marginalia.WmsaHome;
import nu.marginalia.language.encoding.UnicodeNormalization;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.pos.PosPattern;
import nu.marginalia.language.pos.PosPatternCategory;
import nu.marginalia.language.pos.PosTagger;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Singleton
public class LanguageConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(LanguageConfiguration.class);

    private final Map<String, Path> resources = new HashMap<>();
    private final Map<String, LanguageDefinition> languages = new LinkedHashMap<>();
    private final JFastText fastTextLanguageModel = new JFastText();

    public Optional<LanguageDefinition> identifyLanguage(org.jsoup.nodes.Document jsoupDoc) {
        StringBuilder sampleBuilder = new StringBuilder();
        jsoupDoc.body().traverse((node, _) -> {
            if (sampleBuilder.length() > 4096)
                return;
            if (!(node instanceof TextNode tn))
                return;

            sampleBuilder.append(' ').append(tn.text());
        });
        return identifyLanguage(sampleBuilder.toString());
    }

    public Optional<LanguageDefinition> identifyLanguage(String sample) {
        String prediction = fastTextLanguageModel.predict(sample);
        if (null == prediction)
            return Optional.empty();

        if (prediction.length() == "__label__??".length()) {
            String isoCode = prediction.substring("__label__".length());
            return Optional.ofNullable(getLanguage(isoCode));
        }

        return Optional.empty();
    }

    public Optional<LanguageDefinition> identifyLanguage(String sample, String fallbackIsoCode) {
        return identifyLanguage(sample).or(() -> Optional.ofNullable(getLanguage(fallbackIsoCode)));
    }

    public List<LanguageDefinition> languages() {
        return new ArrayList<>(this.languages.values());
    }
    public Map<String, LanguageDefinition> languagesMap() {
        return Collections.unmodifiableMap(languages);
    }
    @Nullable
    public LanguageDefinition getLanguage(String language) {
        return languages.get(language);
    }

    @Inject
    public LanguageConfiguration() throws IOException, ParserConfigurationException, SAXException {
        this(WmsaHome.getLanguageModels(), new LanguageConfigLocation.Auto());
    }

    public LanguageConfiguration(LanguageConfigLocation languageFile) throws IOException, ParserConfigurationException, SAXException {
        this(WmsaHome.getLanguageModels(), languageFile);
    }

    public LanguageConfiguration(LanguageModels lm, LanguageConfigLocation languageFile)
            throws IOException, ParserConfigurationException, SAXException {
        fastTextLanguageModel.loadModel(lm.fasttextLanguageModel.toString());

        try (var languagesXmlStream = languageFile.findLanguageConfiguration(languageFile)) {
            if (languagesXmlStream == null)
                throw new IllegalStateException("languages-default.xml resource not found in classpath");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(languagesXmlStream);

            parseResources(doc);
            parseLanguages(doc);
        }

        logger.info("Loaded language configuration: {}", languages);
    }

    private void parseLanguages(Document doc) {
        NodeList languageNodes = doc.getElementsByTagName("language");

        for (int i = 0; i < languageNodes.getLength(); i++) {
            Element languageTag = (Element) languageNodes.item(i);

            boolean disabled = "TRUE".equalsIgnoreCase(languageTag.getAttribute("disabled"));
            if (disabled)
                continue;

            String isoCode = languageTag.getAttribute("isoCode").toLowerCase();
            String name = languageTag.getAttribute("name");

            try {
                PosTagger posTagger = parsePosTag(languageTag, isoCode);
                Stemmer stemmer = parseStemmerTag(languageTag, posTagger, isoCode);
                KeywordHasher keywordHasher = parseHasherTag(languageTag, isoCode);
                Map<PosPatternCategory, List<PosPattern>> posPatterns =
                        parsePosPatterns(posTagger, languageTag, isoCode);
                UnicodeNormalization unicodeNormalization = parseUnicodeNormalization(languageTag, isoCode);

                languages.put(isoCode,
                        new LanguageDefinition(isoCode, name, stemmer, unicodeNormalization, keywordHasher, posTagger, posPatterns));
            }
            catch (IOException ex) {
                logger.error("Failed to set up language " + isoCode, ex);
            }
        }
    }

    private UnicodeNormalization parseUnicodeNormalization(Element languageTag, String isoCode) {
        NodeList normalizationTags = languageTag.getElementsByTagName("unicodeNormalization");
        if (normalizationTags.getLength() == 0)
            return new UnicodeNormalization.JustNormalizeQuotes();
        Element normalizationTag = (Element) normalizationTags.item(0);
        String algorithm = normalizationTag.getAttribute("algorithm");

        return switch(algorithm) {
            case "minimal" -> new UnicodeNormalization.JustNormalizeQuotes();
            case "e-accents" -> new UnicodeNormalization.FlattenEAccents();
            case "german" -> new UnicodeNormalization.Flattenß();
            case "maximal-latin" -> new UnicodeNormalization.FlattenAllLatin();
            default -> throw new IllegalArgumentException("Invalida algorithm " + algorithm + " on language configuration for " + isoCode);
        };
    }

    private Map<PosPatternCategory, List<PosPattern>> parsePosPatterns(@Nullable PosTagger posTagger,
                                                                       Element languageTag, String isoCode) {
        if (null == posTagger)
            return Map.of();

        Map<PosPatternCategory, List<PosPattern>> ret = new HashMap<>();
        NodeList ngramsElements = languageTag.getElementsByTagName("ngrams");

        for (int i = 0; i < ngramsElements.getLength(); i++) {
            Element ngramsTag = (Element) ngramsElements.item(i);
            String type = ngramsTag.getAttribute("type");

            PosPatternCategory category = switch(type) {
                case "name" -> PosPatternCategory.NAME;
                case "noun" -> PosPatternCategory.NOUN;
                case "keyword" -> PosPatternCategory.KEYWORD;
                case "title" -> PosPatternCategory.TITLE;
                case "subject-suffix" -> PosPatternCategory.SUBJECT_SUFFIX;
                default -> throw new IllegalArgumentException("Invalid ngrams type in " + isoCode + ", what is '" + type + "'?");
            };

            NodeList posPatternsList = ngramsTag.getElementsByTagName("pospattern");
            for (int j = 0; j < posPatternsList.getLength(); j++) {
                Element posPatternTag = (Element) posPatternsList.item(j);
                ret.computeIfAbsent(category, (k) -> new ArrayList<>())
                        .add(new PosPattern(posTagger, posPatternTag.getTextContent()));
            }

        }

        return ret;
    }

    @Nullable
    private PosTagger parsePosTag(Element languageTag, String isoCode) throws IOException {
        NodeList rdrElements = languageTag.getElementsByTagName("rdrTagger");
        if (rdrElements.getLength() < 1) {
            return null;
        }
        else if (rdrElements.getLength() > 1) {
            throw new IllegalStateException("Multiple rdr taggers defined in " + isoCode);
        }
        Element rdrElement = (Element) rdrElements.item(0);

        String dictId = rdrElement.getAttribute("dictId");
        String rdrId = rdrElement.getAttribute("rdrId");

        Path dictPath = resources.get(dictId);
        Path rdrPath = resources.get(rdrId);

        if (null == dictPath)
            throw new IllegalArgumentException("language.xml: dictPath id " + dictId
                    + " does not map to a resource in " + isoCode);
        if (null == rdrPath)
            throw new IllegalArgumentException("language.xml: rdrPath id " + dictId
                    + " does not map to a resource in " + isoCode);

        return new PosTagger(isoCode, dictPath, rdrPath);
    }


    private KeywordHasher parseHasherTag(Element languageElement, String isoCode) {
        NodeList keywordHasherElements = languageElement.getElementsByTagName("keywordHash");
        if (keywordHasherElements.getLength() != 1) {
            throw new IllegalArgumentException(
                    "language.xml: No keywordHasher block for language element " + isoCode);
        }
        Element keywordHasheElement = (Element) keywordHasherElements.item(0);

        String hasherName = keywordHasheElement.getAttribute("algorithm");

        return switch (hasherName) {
            case "asciish" -> new KeywordHasher.AsciiIsh();
            case "utf8" -> new KeywordHasher.Utf8();
            default -> throw new IllegalArgumentException(
                    "language.xml: Unknown keywordHash name " + hasherName + " in " + isoCode);
        };
    }

    private Stemmer parseStemmerTag(Element languageElement, PosTagger posTagger, String isoCode) {
        NodeList stemmerElements = languageElement.getElementsByTagName("stemmer");
        if (stemmerElements.getLength() != 1) {
            throw new IllegalArgumentException(
                    "language.xml: No stemmer block for language element " + isoCode);
        }
        Element stemmerElement = (Element) stemmerElements.item(0);

        String stemmerName = stemmerElement.getAttribute("algorithm");
        String stemmerVariant = stemmerElement.getAttribute("variant");

        PosPattern inclusionPattern = null;
        NodeList posPatternList = stemmerElement.getElementsByTagName("pospattern");
        if (posPatternList.getLength() >= 1) {
            Element posElement =  (Element) posPatternList.item(0);
            inclusionPattern = new PosPattern(posTagger, posElement.getTextContent());
        }

        return switch (stemmerName.toLowerCase()) {
            case "porter" -> new Stemmer.Porter(inclusionPattern);
            case "snowball" -> new Stemmer.Snowball(stemmerVariant, inclusionPattern);
            case "none" -> new Stemmer.NoOpStemmer();
            default -> throw new IllegalArgumentException(
                    "language.xml: Unknown stemmer name " + stemmerName + " in " + isoCode);
        };
    }

    private void parseResources(Document doc) throws IOException {
        NodeList resourceNodes = doc.getElementsByTagName("resource");
        for (int i = 0; i < resourceNodes.getLength(); i++) {
            Element resourceTag = (Element) resourceNodes.item(i);

            String resourceId = resourceTag.getAttribute("id");
            String resourceMd5 = resourceTag.getAttribute("md5");
            Path resourcePath = WmsaHome.getDataPath().resolve(resourceTag.getAttribute("path"));
            String resourceHref = resourceTag.getAttribute("href");

            if (!validateResource(resourcePath, resourceMd5)) {
                boolean success = false;
                try {
                    success = fetchResource(resourceHref, resourcePath, resourceMd5);
                } catch (URISyntaxException | IOException ex) {
                    logger.error(ex.getMessage(), ex);
                    success = false;
                }

                // It's likely if we were to just explode here, that a docker-compose restart:always
                // would put us in a
                // loop that repeatedly fails to download the same file. We'd like to avoid that by
                // stalling and
                // awaiting human intervention.

                while (!success) {
                    logger.error("Stopping to prevent restart loop");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            if (resources.put(resourceId, resourcePath) != null)
                throw new IllegalStateException(
                        "Resource with id " + resourceId + " already exists");
        }
    }

    private boolean fetchResource(String resourceUrl, Path resourcePath, String resourceMd5)
            throws IOException, URISyntaxException {

        Path parentPath = resourcePath.getParent();
        if (!Files.isDirectory(parentPath)) {
            logger.info("Setting up directory {}", parentPath);
            Files.createDirectories(parentPath);
        }

        logger.info("Fetching {}", resourceUrl);

        URL url = new URI(resourceUrl).toURL();
        Path tempFile = Files.createTempFile("resource", "dat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try (InputStream is = conn.getInputStream();
             OutputStream os = Files.newOutputStream(tempFile, StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            is.transferTo(os);
            os.flush();

            String actualMd5 = getFileMD5(tempFile);
            if (!resourceMd5.isBlank() && !Objects.equals(resourceMd5, actualMd5)) {
                logger.error("Freshly downloaded resource {} does not match md5sum {}", resourceUrl,
                        resourceMd5);
                return false;
            } else {
                logger.info("Downloaded resource {} to {} ** md5sum {}", resourceUrl, resourcePath,
                        actualMd5);
                Files.move(tempFile, resourcePath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException ex) {
            logger.error("IOException", ex);
            return false;
        } finally {
            conn.disconnect();
            Files.deleteIfExists(tempFile);
        }
    }

    private boolean validateResource(Path resourcePath, String providedMd5Sum) throws IOException {
        resourcePath = resourcePath.normalize();

        if (!resourcePath.normalize().startsWith(WmsaHome.getDataPath()))
            throw new IllegalArgumentException(
                    "Resource path has escaped $WMSA_HOME/data: " + resourcePath);
        if (!Files.exists(resourcePath)) {
            logger.info("Resource path does not exist: " + resourcePath);
            return false;
        }

        String actualMd5 = getFileMD5(resourcePath);
        if (providedMd5Sum.isBlank()) {
            logger.info("No md5sum provided for resource path: {}, but was calculated to {}",
                    resourcePath, actualMd5);
            return true;
        }

        if (Objects.equals(actualMd5, providedMd5Sum)) {
            return true;
        } else {
            logger.error("MD5 checksum mismatch for {} -- {}", resourcePath, providedMd5Sum);
            return false;
        }
    }

    public String getFileMD5(Path filePath) {
        try (InputStream fis = Files.newInputStream(filePath)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(fis, md);

            // Read the file
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // Reading updates the digest
            }

            byte[] digest = md.digest();

            // Convert to hex
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
