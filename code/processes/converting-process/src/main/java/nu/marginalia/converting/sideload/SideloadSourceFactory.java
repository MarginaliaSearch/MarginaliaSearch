package nu.marginalia.converting.sideload;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.converting.sideload.dirtree.DirtreeSideloaderFactory;
import nu.marginalia.converting.sideload.encyclopedia.EncyclopediaMarginaliaNuSideloader;
import nu.marginalia.converting.sideload.stackexchange.StackexchangeSideloader;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;

public class SideloadSourceFactory {
    private final Gson gson;
    private final SideloaderProcessing sideloaderProcessing;
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentKeywordExtractor documentKeywordExtractor;
    private final DirtreeSideloaderFactory dirtreeSideloaderFactory;

    @Inject
    public SideloadSourceFactory(Gson gson,
                                 SideloaderProcessing sideloaderProcessing,
                                 ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                                 DocumentKeywordExtractor documentKeywordExtractor,
                                 DirtreeSideloaderFactory dirtreeSideloaderFactory) {
        this.gson = gson;
        this.sideloaderProcessing = sideloaderProcessing;
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.documentKeywordExtractor = documentKeywordExtractor;
        this.dirtreeSideloaderFactory = dirtreeSideloaderFactory;
    }

    public SideloadSource sideloadEncyclopediaMarginaliaNu(Path pathToDbFile, String baseUrl) throws SQLException {
        return new EncyclopediaMarginaliaNuSideloader(pathToDbFile, baseUrl, gson, sideloaderProcessing);
    }

    public Collection<? extends SideloadSource> sideloadDirtree(Path pathToYamlFile) throws IOException {
        return dirtreeSideloaderFactory.createSideloaders(pathToYamlFile);
    }

    /** Do not use, this code isn't finished */
    public Collection<? extends SideloadSource> sideloadStackexchange(Path pathToDbFileRoot) throws IOException {
        try (var dirs = Files.walk(pathToDbFileRoot)) {
            return dirs
                .filter(Files::isRegularFile)
                .filter(f -> f.toFile().getName().endsWith(".db"))
                .map(dbFile -> new StackexchangeSideloader(dbFile, sentenceExtractorProvider, documentKeywordExtractor))
                .toList();
        }
    }
}
