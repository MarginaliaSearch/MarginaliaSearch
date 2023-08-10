package nu.marginalia.converting.sideload;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;

import java.nio.file.Path;
import java.sql.SQLException;

public class SideloadSourceFactory {
    private final Gson gson;
    private final HtmlDocumentProcessorPlugin htmlProcessorPlugin;
    private final SentenceExtractor sentenceExtractor;
    private final DocumentKeywordExtractor documentKeywordExtractor;

    @Inject
    public SideloadSourceFactory(Gson gson, HtmlDocumentProcessorPlugin htmlProcessorPlugin, SentenceExtractor sentenceExtractor, DocumentKeywordExtractor documentKeywordExtractor) {
        this.gson = gson;
        this.htmlProcessorPlugin = htmlProcessorPlugin;
        this.sentenceExtractor = sentenceExtractor;
        this.documentKeywordExtractor = documentKeywordExtractor;
    }

    public SideloadSource sideloadEncyclopediaMarginaliaNu(Path pathToDbFile) throws SQLException {
        return new EncyclopediaMarginaliaNuSideloader(pathToDbFile, gson, htmlProcessorPlugin);
    }

    /** Do not use, this code isn't finished */
    @Deprecated()
    public SideloadSource sideloadStackexchange(Path pathTo7zFile, String domainName) {
        return new StackexchangeSideloader(pathTo7zFile, domainName, sentenceExtractor, documentKeywordExtractor);
    }
}
