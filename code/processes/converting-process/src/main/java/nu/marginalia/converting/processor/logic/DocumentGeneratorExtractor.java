package nu.marginalia.converting.processor.logic;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.util.Collections;
import java.util.List;

/** Extract keywords for the document meta generator tag */
public class DocumentGeneratorExtractor {

    private final String defaultValue = "unset";

    public List<String> generatorCleaned(Document doc) {

        String generator = doc
                .select("meta[name=generator]")
                .attr("content");

        // Remove leading or trailing junk from the generator string, "powered by" etc.
        generator = trim(generator);

        if (generator.isBlank())
            return List.of(defaultValue);

        String[] parts = StringUtils.split(generator, " ,:!");
        if (parts.length == 0)
            return List.of(defaultValue);

        int slashIdx = parts[0].indexOf('/');
        if (slashIdx >= 0) {
            // mozilla and staroffice has a really weird format
            return List.of(parts[0].substring(0, slashIdx));
        }

        if (parts.length > 3) {
            return List.of(defaultValue); // if it's still very long after trim(), it's probably a custom hand written message
        }

        switch (parts[0]) {
            case "joomla!":
                return List.of("joomla");
            case "plone":
            case "claris":
            case "one.com":
            case "wix.com":
            case "wpbakery":
                return List.of(parts[0]);
            case "adobe":
            case "microsoft":
                return List.of(parts[1]);
        }

        if (parts.length > 1) {
            return List.of(parts[0], parts[0] + "_" + truncVersion(parts[1]));
        }
        else {
            return List.of(parts[0]);
        }
    }

    private String trim(String generator) {

        generator = generator.toLowerCase().trim();
        if (generator.startsWith("powered by ")) {
            generator = generator.substring("powered by ".length());
        }

        int dashIdx = generator.indexOf('-'); // Some strings have values like 'foobar 2.3 - the free online generator!'
        if (dashIdx >= 0) {
            generator = generator.substring(0, dashIdx);
        }

        if (!StringUtils.isAsciiPrintable(generator))
            return "";

        return generator;
    }

    // Censor exact version strings, being able to search by major version is enough
    // for any non-blackhat purpose
    private String truncVersion(String part) {
        int periodIdx = part.indexOf('.', part.startsWith("0.") ? 2 : 0);

        if (periodIdx < 0)
            return part;

        return part.substring(0, periodIdx);
    }

}
