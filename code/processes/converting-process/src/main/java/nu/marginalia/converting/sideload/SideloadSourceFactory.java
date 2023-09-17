package nu.marginalia.converting.sideload;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.converting.sideload.dirtree.DirtreeSideloaderFactory;
import nu.marginalia.converting.sideload.encyclopedia.EncyclopediaMarginaliaNuSideloader;
import nu.marginalia.converting.sideload.stackexchange.StackexchangeSideloader;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;

public class SideloadSourceFactory {
    private final Gson gson;
    private final SideloaderProcessing sideloaderProcessing;
    private final SentenceExtractor sentenceExtractor;
    private final DocumentKeywordExtractor documentKeywordExtractor;
    private final DirtreeSideloaderFactory dirtreeSideloaderFactory;

    @Inject
    public SideloadSourceFactory(Gson gson,
                                 SideloaderProcessing sideloaderProcessing,
                                 SentenceExtractor sentenceExtractor,
                                 DocumentKeywordExtractor documentKeywordExtractor,
                                 DirtreeSideloaderFactory dirtreeSideloaderFactory) {
        this.gson = gson;
        this.sideloaderProcessing = sideloaderProcessing;
        this.sentenceExtractor = sentenceExtractor;
        this.documentKeywordExtractor = documentKeywordExtractor;
        this.dirtreeSideloaderFactory = dirtreeSideloaderFactory;
    }

    public SideloadSource sideloadEncyclopediaMarginaliaNu(Path pathToDbFile) throws SQLException {
        return new EncyclopediaMarginaliaNuSideloader(pathToDbFile, gson, sideloaderProcessing);
    }

    public Collection<? extends SideloadSource> sideloadDirtree(Path pathToYamlFile) throws IOException {
        return dirtreeSideloaderFactory.createSideloaders(pathToYamlFile);
    }

    /** Do not use, this code isn't finished */
    @Deprecated()
    public SideloadSource sideloadStackexchange(Path pathTo7zFile, String domainName) {
        return new StackexchangeSideloader(pathTo7zFile, domainName, sentenceExtractor, documentKeywordExtractor);
    }
}
