package nu.marginalia.language.config;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class LanguageConfiguration {
    private final Map<String, Language> languages = new HashMap<>();

    @Inject
    public LanguageConfiguration() {
        Path languageConfigurationFile = WmsaHome.getDataPath().resolve("language.xml");

        // TODO: read the xml

        // for now:
        languages.put("en", new Language("en", "English", true));
        languages.put("sv", new Language("sv", "Swedish/Svenska", true));
    }

    @Nullable
    public Language getLanguage(String language) {
        return languages.get(language);
    }

    public record Language(String isoCode, String name, boolean permitted) {
    }
}
