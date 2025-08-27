package nu.marginalia.keyword.extractors;

import com.google.common.collect.Sets;
import nu.marginalia.WmsaHome;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.util.TestLanguageModels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubjectLikeKeywordsTest {
    String text = """
            In 60 BC, Caesar, Crassus, and Pompey formed the First Triumvirate, an informal political alliance that 
            dominated Roman politics for several years. Their attempts to amass power as Populares were opposed by 
            the Optimates within the Roman Senate, among them Cato the Younger with the frequent support of Cicero. 
            Caesar rose to become one of the most powerful politicians in the Roman Republic through a string of 
            military victories in the Gallic Wars, completed by 51 BC, which greatly extended Roman territory. 
            During this time he both invaded Britain and built a bridge across the Rhine river. These achievements 
            and the support of his veteran army threatened to eclipse the standing of Pompey, who had realigned himself 
            with the Senate after the death of Crassus in 53 BC. With the Gallic Wars concluded, the Senate ordered 
            Caesar to step down from his military command and return to Rome. In 49 BC, Caesar openly defied the 
            Senate's authority by crossing the Rubicon and marching towards Rome at the head of an army. This 
            began Caesar's civil war, which he won, leaving him in a position of near unchallenged power and 
            influence in 45 BC.
            
            After assuming control of government, Caesar began a program of social and governmental reforms,
            including the creation of the Julian calendar. He gave citizenship to many residents of far regions
            of the Roman Republic. He initiated land reform and support for veterans. He centralized the 
            bureaucracy of the Republic and was eventually proclaimed "dictator for life" (dictator perpetuo).
            His populist and authoritarian reforms angered the elites, who began to conspire against him. On the
            Ides of March (15 March) 44 BC, Caesar was assassinated by a group of rebellious senators led by Brutus 
            and Cassius, who stabbed him to death. A new series of civil wars broke out and the constitutional
            government of the Republic was never fully restored. Caesar's great-nephew and adopted heir Octavian,
            later known as Augustus, rose to sole power after defeating his opponents in the last civil war of
            the Roman Republic. Octavian set about solidifying his power, and the era of the Roman Empire began.
           """;

    static SentenceExtractor se;

    @BeforeAll
    public static void setUpAll() throws IOException, ParserConfigurationException, SAXException {
        se = new SentenceExtractor(new LanguageConfiguration(WmsaHome.getLanguageModels()), WmsaHome.getLanguageModels());
    }

    @Test
    public void test() throws IOException {
        var lm = TestLanguageModels.getLanguageModels();
        var dict = new TermFrequencyDict(lm);

        var dld = se.extractSentences(text, "Julius Caesar");

        WordsTfIdfCounts tfIdfCounts = new WordsTfIdfCounts(dict, new KeywordExtractor(dld.language()), dld);
        SubjectLikeKeywords keywords = new SubjectLikeKeywords(new KeywordExtractor(dld.language()),
                tfIdfCounts,
                dld);

        Set<String> actual = keywords.getReps().stream().map(rep -> rep.word).collect(Collectors.toSet());
        Set<String> expected = Set.of("populares", "republic", "authoritarian_reforms", "senate", "pompey", "civil_wars", "octavian", "caesar");

        System.out.println(actual);
        System.out.println(expected);

        assertEquals(Collections.emptySet(), Sets.symmetricDifference(actual, expected));
    }

}