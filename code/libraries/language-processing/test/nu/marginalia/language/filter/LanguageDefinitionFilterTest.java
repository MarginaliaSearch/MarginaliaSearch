package nu.marginalia.language.filter;

import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.DocumentLanguageData;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageDefinitionFilterTest {

    @Test
    void isPageInteresting() throws IOException, ParserConfigurationException, SAXException {
        var languageFilter = new LanguageFilter(TestLanguageModels.getLanguageModels(), new LanguageConfiguration());

        assertEquals(Optional.empty(), languageFilter.predictLanguage(new DocumentLanguageData(List.of(), "Carlos fue al bosque y recogió bayas")));
        assertEquals(Optional.empty(), languageFilter.predictLanguage(new DocumentLanguageData(List.of(), "Charlie est allé dans la forêt et a cueilli des baies")));
        assertEquals(Optional.of("sv"), languageFilter.predictLanguage(new DocumentLanguageData(List.of(), "Kalle gick i skogen och plockade bär")));
        assertEquals(Optional.of("en"), languageFilter.predictLanguage(new DocumentLanguageData(List.of(), "Charlie went to the woods to go berry-picking")));
    }

}