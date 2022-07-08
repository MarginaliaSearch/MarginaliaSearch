package nu.marginalia.wmsa.memex.change;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import nu.marginalia.gemini.GeminiServiceImpl;
import nu.marginalia.gemini.gmi.GemtextDocument;
import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.memex.Memex;
import nu.marginalia.wmsa.memex.MemexData;
import nu.marginalia.wmsa.memex.MemexLoader;
import nu.marginalia.wmsa.memex.change.update.GemtextDocumentUpdateCalculator;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.renderer.MemexRendererers;
import nu.marginalia.wmsa.memex.system.MemexFileSystemModifiedTimes;
import nu.marginalia.wmsa.memex.system.MemexFileWriter;
import nu.marginalia.wmsa.memex.system.MemexSourceFileSystem;
import nu.marginalia.wmsa.memex.system.git.MemexGitRepoImpl;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemtextTaskUpdateTest {


    private Memex memex;
    private Path tempDir;

    private final String tombstonePath = "/special/tombstone.gmi";
    private final String redirectPath = "/special/redirects.gmi";
    private final String testFilePath = "/test.gmi";
    private final String todoFilePath = "/todo.gmi";
    private final String doneFilePath = "/done.gmi";

    static final Logger logger = LoggerFactory.getLogger(GemtextTaskUpdateTest.class);

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

        memex = new Memex(data, null, Mockito.mock(MemexGitRepoImpl.class), new MemexLoader(data, new MemexFileSystemModifiedTimes(),
                new MemexSourceFileSystem(tempDir, Mockito.mock(MemexGitRepoImpl.class)), tempDir, tombstonePath, redirectPath),
                Mockito.mock(MemexFileWriter.class),
                null,
                Mockito.mock(MemexRendererers.class),
                Mockito.mock(GeminiServiceImpl.class));
    }

    @SneakyThrows
    @AfterEach
    public void tearDown() {
        TestUtil.clearTempDir(tempDir);
    }

    @Test
    void updateTodoFileWithTodoTask() throws IOException {

        var url = new MemexNodeUrl(testFilePath);

        GemtextMutation.createOrAppend(url, "%%%TASKS\n# Header", new MemexNodeHeadingId(1),
                "%%% TASKS", "## Todo", "- A task yet finished").visit(memex);

        GemtextDocumentUpdateCalculator updateCalculator = new GemtextDocumentUpdateCalculator(memex);
        var updates = updateCalculator.calculateUpdates(memex.getDocument(url), MemexNodeHeadingId.ROOT,
                GemtextDocument.of(url, "%%% TASKS", "# Header", "## Todo", "- A task yet finished (?)", "- One More Task"));
        updates.forEach(System.out::println);
        for (var update : updates) {
            update.visit(memex);
        }

        verifyFile(testFilePath,
                "%%% TASKS",
                "# Header",
                "## Todo",
                "- A task yet finished (?)",
                "- One More Task"
                );
    }

    @Test
    void updateDoneFileWithTodoTask() throws IOException {

        var url = new MemexNodeUrl(testFilePath);

        GemtextMutation.createOrAppend(url, "%%% TASKS\n# Header", new MemexNodeHeadingId(1),
                "## Done", "- A task yet finished (/)").visit(memex);

        GemtextDocumentUpdateCalculator updateCalculator = new GemtextDocumentUpdateCalculator(memex);
        var updates = updateCalculator.calculateUpdates(memex.getDocument(url), MemexNodeHeadingId.ROOT,
                GemtextDocument.of(url, "%%% TASKS", "# Header", "## Done", "- A task yet finished (?)"));
        updates.forEach(System.out::println);
        for (var update : updates) {
            update.visit(memex);
        }

        verifyFile(testFilePath,
                "%%% TASKS",
                "# Header",
                "## Done"
        );

        verifyFile(todoFilePath,
                "%%% TASKS",
                "# Todo",
                "- A task yet finished (?)"
                );
    }

    @Test
    void moveToDoneNewDoneFile() throws IOException {

        var url = new MemexNodeUrl(testFilePath);

        GemtextMutation.createOrAppend(url, "%%% TASKS\n# Header", new MemexNodeHeadingId(1),
                "%%% TASKS","## Todo", "- A task yet finished").visit(memex);

        GemtextDocumentUpdateCalculator updateCalculator = new GemtextDocumentUpdateCalculator(memex);
        var updates = updateCalculator.calculateUpdates(memex.getDocument(url), MemexNodeHeadingId.ROOT,
                GemtextDocument.of(url, "%%% TASKS", "# Header", "## Todo", "- A task yet finished (/)"));
        updates.forEach(System.out::println);
        for (var update : updates) {
            update.visit(memex);
        }

        verifyFile(doneFilePath,
                "%%% TASKS",
                "# Done",
                "",
                "## Done " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "- A task yet finished (/)");

        updates = updateCalculator.calculateUpdates(memex.getDocument(url), MemexNodeHeadingId.ROOT,
                GemtextDocument.of(url, "%%% TASKS", "# Header", "## Todo", "- Another task yet finished (/)"));
        updates.forEach(System.out::println);
        for (var update : updates) {
            update.visit(memex);
        }

        verifyFile(doneFilePath,
                "%%% TASKS",
                "# Done",
                "",
                "## Done " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "- Another task yet finished (/)",
                "- A task yet finished (/)");
    }

    @Test
    void moveToDoneOldDoneFile() throws IOException {

        var doneUrl = new MemexNodeUrl(doneFilePath);
        var url = new MemexNodeUrl(testFilePath);


        GemtextMutation.createOrAppend(doneUrl, "%%% TASKS\n# Done", new MemexNodeHeadingId(1),
                "## Done 2012-04-30", "- A very old task (/)").visit(memex);

        GemtextMutation.createOrAppend(url, "%%% TASKS\n# Header", new MemexNodeHeadingId(1),
                "## Todo", "- A task yet finished").visit(memex);

        GemtextDocumentUpdateCalculator updateCalculator = new GemtextDocumentUpdateCalculator(memex);
        var updates = updateCalculator.calculateUpdates(memex.getDocument(url), MemexNodeHeadingId.ROOT,
                GemtextDocument.of(url, "%%% TASKS", "# Header", "## Todo", "- A task yet finished (/)"));
        updates.forEach(System.out::println);
        for (var update : updates) {
            update.visit(memex);
        }

        verifyFile(doneFilePath,
                "%%% TASKS",
                "# Done",
                "",
                "## Done " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "- A task yet finished (/)",
                "## Done 2012-04-30",
                "- A very old task (/)");

    }

    public void verifyFile(String file, String... lines) throws IOException {
        Path p = Path.of(tempDir + file);
        assertTrue(Files.exists(p), "File " + file + " is missing");
        List<String> actualLines = Files.readAllLines(p);
        System.out.println("Expecting: ");
        Arrays.stream(lines).forEach(System.out::println);
        System.out.println("Got: ");
        actualLines.forEach(System.out::println);
        System.out.println("-- end -- ");

        assertEquals(lines.length, actualLines.size());
        for (int i = 0; i < lines.length; i++) {
            assertEquals(lines[i], actualLines.get(i));
        }
    }

}