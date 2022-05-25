package nu.marginalia.wmsa.memex.change;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import nu.marginalia.gemini.GeminiService;
import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.memex.*;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.renderer.MemexRendererers;
import nu.marginalia.wmsa.memex.system.MemexFileSystemModifiedTimes;
import nu.marginalia.wmsa.memex.system.MemexFileWriter;
import nu.marginalia.wmsa.memex.system.MemexGitRepo;
import nu.marginalia.wmsa.memex.system.MemexSourceFileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


class GemtextChangeTest {


    private Memex memex;
    private Path tempDir;

    private final String tombstonePath = "/special/tombstone.gmi";
    private final String redirectPath = "/special/redirects.gmi";
    private final String testFilePath = "/test.gmi";

    static final Logger logger = LoggerFactory.getLogger(GemtextChangeTest.class);

    @BeforeAll
    public static void init() {

        RxJavaPlugins.setErrorHandler(e -> {
            if (e.getMessage() == null) {
                logger.error("Error", e);
            }
            else {
                logger.error("Error {}: {}", e.getClass().getSimpleName(), e.getMessage());
            }
        });
    }

    @SneakyThrows
    @BeforeEach
    public void setUp() {
        tempDir = Files.createTempDirectory("test");
        Files.createDirectory(tempDir.resolve("special"));
        var data = new MemexData();

        memex = new Memex(data, null,
                Mockito.mock(MemexGitRepo.class), new MemexLoader(data, new MemexFileSystemModifiedTimes(),
                    new MemexSourceFileSystem(tempDir, Mockito.mock(MemexGitRepo.class)),
                tempDir, tombstonePath, redirectPath),
                Mockito.mock(MemexFileWriter.class),
                null,
                Mockito.mock(MemexRendererers.class),
                Mockito.mock(GeminiService.class));
    }

    @SneakyThrows
    @AfterEach
    public void tearDown() {
        TestUtil.clearTempDir(tempDir);
    }

    @Test
    void appendHeading() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "## Header 2", "# Header 3"
                })
        ).visit(memex);
        new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] { "3"}).visit(memex);


        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));
        lines.forEach(System.out::println);
        assertEquals(5, lines.size());
        assertEquals("# Header", lines.get(0));
        assertEquals("1", lines.get(1));
        assertEquals("## Header 2", lines.get(2));
        assertEquals("3", lines.get(3));
        assertEquals("# Header 3", lines.get(4));
    }

    @Test
    void appendRoot() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "## Header 2", "# Header 3"
                })
        ).visit(memex);
        new GemtextAppend(url, new MemexNodeHeadingId(0), new String[] { "3"})
                .visit(memex);


        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));
        lines.forEach(System.out::println);
        assertEquals(5, lines.size());
        assertEquals("# Header", lines.get(0));
        assertEquals("1", lines.get(1));
        assertEquals("## Header 2", lines.get(2));
        assertEquals("# Header 3", lines.get(3));
        assertEquals("3", lines.get(4));
    }

    @Test
    void appendMissing() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "## Header 2", "# Header 3"
                })
        ).visit(memex);
        new GemtextAppend(url, new MemexNodeHeadingId(5), new String[] { "3"})
                .visit(memex);


        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));
        lines.forEach(System.out::println);
        assertEquals(5, lines.size());
        assertEquals("# Header", lines.get(0));
        assertEquals("1", lines.get(1));
        assertEquals("## Header 2", lines.get(2));
        assertEquals("# Header 3", lines.get(3));
        assertEquals("3", lines.get(4));
    }

    @Test
    void replaceHeading() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "## Header 2", "# Header 3"
                })
        ).visit(memex);
        new GemtextReplace(url, new MemexNodeHeadingId(1), new String[] { "# New", "3"})
                .visit(memex);


        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));
        lines.forEach(System.out::println);
        assertEquals(3, lines.size());
        assertEquals("# New", lines.get(0));
        assertEquals("3", lines.get(1));
        assertEquals("# Header 3", lines.get(2));
    }

    @Test
    void replaceRoot() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "## Header 2", "# Header 3"
                })
        ).visit(memex);
        new GemtextReplace(url, new MemexNodeHeadingId(0), new String[] { "# New", "3"})
                .visit(memex);


        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));
        lines.forEach(System.out::println);
        assertEquals(2, lines.size());
        assertEquals("# New", lines.get(0));
        assertEquals("3", lines.get(1));
    }

    @Test
    void replaceMissing() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "## Header 2", "# Header 3"
                })
        ).visit(memex);
        new GemtextReplace(url, new MemexNodeHeadingId(5), new String[] { "# New", "3"})
                .visit(memex);


        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));
        lines.forEach(System.out::println);
        assertEquals(7, lines.size());


        assertEquals("# Header", lines.get(0));
        assertEquals("1", lines.get(1));
        assertEquals("## Header 2", lines.get(2));
        assertEquals("# Header 3", lines.get(3));

        assertEquals("# Error! Replace failed!", lines.get(4));
        assertEquals("# New", lines.get(5));
        assertEquals("3", lines.get(6));
    }

    @Test
    void prependHeading() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                    new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                            "1", "2"
                    })
                ).visit(memex);
        new GemtextPrepend(url, new MemexNodeHeadingId(1), new String[] { "3"})
                .visit(memex);


        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));
        lines.forEach(System.out::println);
        assertEquals(4, lines.size());
        assertEquals("# Header", lines.get(0));
        assertEquals("3", lines.get(1));
        assertEquals("1", lines.get(2));
        assertEquals("2", lines.get(3));
    }

    @Test
    void prependRoot() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "2"
                })
        ).visit(memex);
        new GemtextPrepend(url, new MemexNodeHeadingId(0), new String[] { "3" })
                .visit(memex);

        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));

        lines.forEach(System.out::println);
        assertEquals(4, lines.size());
        assertEquals("3", lines.get(0));
        assertEquals("# Header", lines.get(1));
        assertEquals("1", lines.get(2));
        assertEquals("2", lines.get(3));
    }


    @Test
    void prependMissing() throws IOException {
        var url = new MemexNodeUrl(testFilePath);
        new GemtextCreateOrMutate(url, "# Header",
                new GemtextAppend(url, new MemexNodeHeadingId(1), new String[] {
                        "1", "2"
                })
        ).visit(memex);
        new GemtextPrepend(url, new MemexNodeHeadingId(5), new String[] { "3" })
                .visit(memex);

        List<String> lines = Files.readAllLines(Path.of(tempDir + testFilePath));

        lines.forEach(System.out::println);
        assertEquals(4, lines.size());
        assertEquals("# Header", lines.get(0));
        assertEquals("1", lines.get(1));
        assertEquals("2", lines.get(2));
        assertEquals("3", lines.get(3));
    }
}