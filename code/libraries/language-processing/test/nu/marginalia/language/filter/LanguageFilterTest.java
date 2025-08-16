package nu.marginalia.language.filter;

import nu.marginalia.language.model.DocumentLanguageData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageFilterTest {

    @Test
    void isPageInteresting() {
        var languageFilter = new LanguageFilter(TestLanguageModels.getLanguageModels());

        assertEquals(0., languageFilter.dictionaryAgreement(new DocumentLanguageData(List.of(), "Kalle fue al bosque y recogió bayas")));
        assertEquals(0., languageFilter.dictionaryAgreement(new DocumentLanguageData(List.of(), "Kalle est allé dans la forêt et a cueilli des baies")));
        assertEquals(1.0, languageFilter.dictionaryAgreement(new DocumentLanguageData(List.of(), "Kalle gick i skogen och plockade bär")));
        assertEquals(1.0, languageFilter.dictionaryAgreement(new DocumentLanguageData(List.of(), "Charlie went to the woods to go berry-picking")));
    }

}