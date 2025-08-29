package nu.marginalia.keyword.extractors;

import com.google.common.collect.Sets;
import nu.marginalia.WmsaHome;
import nu.marginalia.dom.DomPruningFilter;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.language.sentence.SentenceExtractor;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameLikeKeywordsTest {
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
    static LanguageConfiguration lc;
    static LanguageDefinition en;


    @BeforeAll
    public static void setUpAll() throws IOException, ParserConfigurationException, SAXException {
        se = new SentenceExtractor(new LanguageConfiguration(WmsaHome.getLanguageModels()), WmsaHome.getLanguageModels());
        lc = new LanguageConfiguration(WmsaHome.getLanguageModels());
        en = lc.getLanguage("en");
    }

    @Test
    public void test() {
        NameLikeKeywords keywords = new NameLikeKeywords(se.extractSentences(text, "Julius Caesar"), 2);
        Set<String> actual = keywords.getReps().stream().map(rep -> rep.word).collect(Collectors.toSet());
        Set<String> expected = Set.of("caesar", "senate", "roman", "republic", "roman_republic");

        System.out.println(actual);
        System.out.println(expected);
        assertEquals(Collections.emptySet(), Sets.symmetricDifference(actual, expected));
    }

    @Test
    public void testWikiArticle() throws IOException, UnsupportedLanguageException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/java.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0));

        var nameWords = new NameLikeKeywords(se.extractSentences(doc), 2);
        System.out.println("Names: " + nameWords.words());
    }

    @Test
    public void testWikiArticleP1() throws UnsupportedLanguageException {
        String html = """
                <p><b>Java</b> is a high-level, class-based, object-oriented programming language that is designed to have as few implementation dependencies as possible. It is a general-purpose programming language intended to let programmers <i>write once, run anywhere</i> (WORA), meaning that compiled Java code can run on all platforms that support Java without the need to recompile. Java applications are typically compiled to bytecode that can run on any Java virtual machine (JVM) regardless of the underlying computer architecture. The syntax of Java is similar to C and C++, but has fewer low-level facilities than either of them. The Java runtime provides dynamic capabilities (such as reflection and runtime code modification) that are typically not available in traditional compiled languages.  As of 2019 , Java was one of the most popular programming languages in use according to GitHub, particularly for client–server web applications, with a reported 9 million developers.</p>
                                <p>Java was originally developed by James Gosling at Sun Microsystems. It was released in May 1995 as a core component of Sun Microsystems' Java platform. The original and reference implementation Java compilers, virtual machines, and class libraries were originally released by Sun under proprietary licenses. As of May 2007, in compliance with the specifications of the Java Community Process, Sun had relicensed most of its Java technologies under the GPL-2.0-only license. Oracle offers its own HotSpot Java Virtual Machine, however the official reference implementation is the OpenJDK JVM which is free open-source software and used by most developers and is the default JVM for almost all Linux distributions.</p>
                                <p>As of September   2023 , Java 21 is the latest version, while Java 17, 11 and 8 are the current long-term support (LTS) versions.</p>""";
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0));

        var nameWords = new NameLikeKeywords(se.extractSentences(doc), 2);
        System.out.println("Names: " + nameWords.words());
    }
}