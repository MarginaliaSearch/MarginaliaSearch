package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.processor.ConverterDomainTypes;
import nu.marginalia.converting.processor.logic.DocumentGeneratorExtractor;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.util.Set;

@Singleton
public class HtmlProcessorSpecializations {
    private final ConverterDomainTypes domainTypes;
    private final LemmySpecialization lemmySpecialization;
    private final XenForoSpecialization xenforoSpecialization;
    private final PhpBBSpecialization phpBBSpecialization;
    private final JavadocSpecialization javadocSpecialization;
    private final MariadbKbSpecialization mariadbKbSpecialization;
    private final WikiSpecialization wikiSpecialization;
    private final BlogSpecialization blogSpecialization;
    private final DefaultSpecialization defaultSpecialization;

    @Inject
    public HtmlProcessorSpecializations(ConverterDomainTypes domainTypes,
                                        LemmySpecialization lemmySpecialization,
                                        XenForoSpecialization xenforoSpecialization,
                                        PhpBBSpecialization phpBBSpecialization,
                                        JavadocSpecialization javadocSpecialization,
                                        MariadbKbSpecialization mariadbKbSpecialization,
                                        WikiSpecialization wikiSpecialization,
                                        BlogSpecialization blogSpecialization,
                                        DefaultSpecialization defaultSpecialization) {
        this.domainTypes = domainTypes;
        this.lemmySpecialization = lemmySpecialization;
        this.xenforoSpecialization = xenforoSpecialization;
        this.phpBBSpecialization = phpBBSpecialization;
        this.javadocSpecialization = javadocSpecialization;
        this.mariadbKbSpecialization = mariadbKbSpecialization;
        this.wikiSpecialization = wikiSpecialization;
        this.blogSpecialization = blogSpecialization;
        this.defaultSpecialization = defaultSpecialization;
    }

    /** Depending on the generator tag, we may want to use specialized logic for pruning and summarizing the document */
    public HtmlProcessorSpecializationIf select(
            DocumentGeneratorExtractor.DocumentGenerator generator,
            EdgeUrl url)
    {

        if (domainTypes.isBlog(url.domain)) {
            return blogSpecialization;
        }

        if (url.domain.getDomain().equals("mariadb.com")
                && url.path.startsWith("/kb")) {
            return mariadbKbSpecialization;
        }

        if (generator.keywords().contains("lemmy")) {
            return lemmySpecialization;
        }
        if (generator.keywords().contains("xenforo")) {
            return xenforoSpecialization;
        }
        if (generator.keywords().contains("phpbb")) {
            return phpBBSpecialization;
        }
        if (generator.keywords().contains("javadoc")) {
            return javadocSpecialization;
        }
        if (generator.type() == GeneratorType.WIKI) {
            return wikiSpecialization;
        }

        return defaultSpecialization;
    }

    /** This interface is used to specify how to process a specific website.
     *  The implementations of this interface are used by the HtmlProcessor to
     *  process the HTML documents.
     */
    public interface HtmlProcessorSpecializationIf {
        Document prune(Document original);
        String getSummary(Document original,
                          Set<String> importantWords);

        default boolean shouldIndex(EdgeUrl url) { return true; }
        default double lengthModifier() { return 1.0; }

        default void amendWords(Document doc, DocumentKeywordsBuilder words) {}

    }
}
