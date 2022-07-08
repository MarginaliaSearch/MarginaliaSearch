package nu.marginalia.wmsa.memex.change;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import lombok.SneakyThrows;
import nu.marginalia.gemini.GeminiServiceImpl;
import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.memex.Memex;
import nu.marginalia.wmsa.memex.MemexData;
import nu.marginalia.wmsa.memex.MemexLoader;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


class GemtextTombstoneUpdateCaclulatorTest {

    private GemtextTombstoneUpdateCaclulator updateCaclulator;
    private Memex memex;
    private Path tempDir;

    private final String tombstonePath = "/special/tombstone.gmi";
    private final String redirectPath = "/special/redirects.gmi";

    static final Logger logger = LoggerFactory.getLogger(GemtextTombstoneUpdateCaclulatorTest.class);

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

        updateCaclulator = new GemtextTombstoneUpdateCaclulator(
                tombstonePath,
                redirectPath
        );
        var data = new MemexData();

        memex = new Memex(data, null,
                Mockito.mock(MemexGitRepoImpl.class),
                new MemexLoader(data, new MemexFileSystemModifiedTimes(),
                    new MemexSourceFileSystem(tempDir, Mockito.mock(MemexGitRepoImpl.class)), tempDir, tombstonePath, redirectPath),
                Mockito.mock(MemexFileWriter.class),
                updateCaclulator,
                Mockito.mock(MemexRendererers.class),
                Mockito.mock(GeminiServiceImpl.class));
    }

    @SneakyThrows
    @AfterEach
    public void tearDown() {
        TestUtil.clearTempDir(tempDir);
    }

    @Test
    void addTombstone() throws IOException {
        updateCaclulator.addTombstone(new MemexNodeUrl("/deleted.gmi"), "It's gone jimmy").visit(memex);
        updateCaclulator.addTombstone(new MemexNodeUrl("/deleted2.gmi"), "RIP").visit(memex);
        List<String> lines = Files.readAllLines(Path.of(tempDir + tombstonePath));
        assertEquals(3, lines.size());

        assertEquals("# Tombstones", lines.get(0));
        assertEquals("=> /deleted.gmi\tIt's gone jimmy", lines.get(1));
        assertEquals("=> /deleted2.gmi\tRIP", lines.get(2));
    }

    @Test
    void addRedirect() throws IOException {
        updateCaclulator.addRedirect(new MemexNodeUrl("/deleted.gmi"), "/new").visit(memex);
        updateCaclulator.addRedirect(new MemexNodeUrl("/deleted2.gmi"), "/new2").visit(memex);
        List<String> lines = Files.readAllLines(Path.of(tempDir + redirectPath));

        assertEquals(3, lines.size());
        assertEquals("# Redirects", lines.get(0));
        assertEquals("=> /deleted.gmi\t/new", lines.get(1));
        assertEquals("=> /deleted2.gmi\t/new2", lines.get(2));
    }
}