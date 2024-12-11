package nu.marginalia.converting.sideload;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.sideload.dirtree.DirtreeSideloaderFactory;
import nu.marginalia.converting.sideload.encyclopedia.EncyclopediaMarginaliaNuSideloader;
import nu.marginalia.converting.sideload.reddit.RedditSideloader;
import nu.marginalia.converting.sideload.stackexchange.StackexchangeSideloader;
import nu.marginalia.converting.sideload.warc.WarcSideloader;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class SideloadSourceFactory {
    private final Gson gson;
    private final SideloaderProcessing sideloaderProcessing;
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentKeywordExtractor documentKeywordExtractor;
    private final AnchorTextKeywords anchorTextKeywords;
    private final AnchorTagsSourceFactory anchorTagsSourceFactory;
    private final DirtreeSideloaderFactory dirtreeSideloaderFactory;

    @Inject
    public SideloadSourceFactory(Gson gson,
                                 SideloaderProcessing sideloaderProcessing,
                                 ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                                 DocumentKeywordExtractor documentKeywordExtractor,
                                 AnchorTextKeywords anchorTextKeywords,
                                 AnchorTagsSourceFactory anchorTagsSourceFactory,
                                 DirtreeSideloaderFactory dirtreeSideloaderFactory) {
        this.gson = gson;
        this.sideloaderProcessing = sideloaderProcessing;
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.documentKeywordExtractor = documentKeywordExtractor;
        this.anchorTextKeywords = anchorTextKeywords;
        this.anchorTagsSourceFactory = anchorTagsSourceFactory;
        this.dirtreeSideloaderFactory = dirtreeSideloaderFactory;
    }

    public SideloadSource sideloadEncyclopediaMarginaliaNu(Path pathToDbFile, String baseUrl) throws SQLException {
        return new EncyclopediaMarginaliaNuSideloader(pathToDbFile, baseUrl, gson, anchorTagsSourceFactory, anchorTextKeywords, sideloaderProcessing);
    }

    public Collection<? extends SideloadSource> sideloadDirtree(Path pathToYamlFile) throws IOException {
        return dirtreeSideloaderFactory.createSideloaders(pathToYamlFile);
    }

    public Collection<? extends SideloadSource> sideloadWarc(Path pathToWarcFiles) throws IOException {
        return sideload(pathToWarcFiles,
                new PathSuffixPredicate(".warc", ".warc.gz"),
                (Path file) -> new WarcSideloader(file, sideloaderProcessing)
        );
    }

    public SideloadSource sideloadReddit(Path pathToDbFiles) throws IOException {
        return sideload(pathToDbFiles,
                new PathSuffixPredicate(".db"),
                (List<Path> paths) -> new RedditSideloader(paths, anchorTextKeywords, sideloaderProcessing));
    }

    public Collection<? extends SideloadSource> sideloadStackexchange(Path pathToDbFileRoot) throws IOException {
        return sideload(pathToDbFileRoot,
                new PathSuffixPredicate(".db"),
                (Path dbFile) -> new StackexchangeSideloader(dbFile, sentenceExtractorProvider, documentKeywordExtractor)
        );
    }

    interface SideloadConstructorMany {
        SideloadSource construct(List<Path> paths) throws IOException;
    }

    interface SideloadConstructorSingle {
        SideloadSource construct(Path paths) throws IOException;
    }

    Collection<? extends SideloadSource> sideload(
            Path path,
            Predicate<Path> pathPredicate,
            SideloadConstructorSingle constructor
    ) throws IOException {
        if (Files.isRegularFile(path)) {
            return List.of(constructor.construct(path));
        }
        else if (Files.isDirectory(path)) {
            try (var dirs = Files.walk(path)) {
                List<SideloadSource> sideloadSources = new ArrayList<>();
                dirs
                        .filter(Files::isRegularFile)
                        .filter(pathPredicate)
                        .forEach(file -> {
                            try {
                                sideloadSources.add(constructor.construct(file));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                return sideloadSources;
            }
        }
        else { // unix socket, etc
            throw new IllegalArgumentException("Path to stackexchange db file(s) must be a file or directory");
        }
    }

    SideloadSource sideload(
            Path path,
            Predicate<Path> pathPredicate,
            SideloadConstructorMany constructor
    ) throws IOException {
        if (Files.isRegularFile(path)) {
            return constructor.construct(List.of(path));
        }
        else if (Files.isDirectory(path)) {
            try (var dirs = Files.walk(path)) {
                var paths = dirs
                        .filter(Files::isRegularFile)
                        .filter(pathPredicate)
                        .toList();

                return constructor.construct(paths);
            }
        }
        else { // unix socket, etc
            throw new IllegalArgumentException("Path to stackexchange db file(s) must be a file or directory");
        }
    }


    private static class PathSuffixPredicate implements Predicate<Path> {
        private final List<String> endings;

        public PathSuffixPredicate(String... endings) {
            this.endings = List.of(endings);
        }

        @Override
        public boolean test(Path path) {
            String fileName = path.toFile().getName();

            return endings.stream().anyMatch(fileName::endsWith);
        }
    }
}
