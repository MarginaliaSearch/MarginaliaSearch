package nu.marginalia.svc;

import io.jooby.Context;
import io.jooby.value.Value;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutorFileTransferServiceTest {

    private Path storageDir;
    private FileStorageService fileStorageService;
    private ExecutorFileTransferService transferService;

    @BeforeEach
    void setUp() throws Exception {
        storageDir = Files.createTempDirectory("transfer-test");
        System.setProperty("storage.root", storageDir.toString());

        // FileStorage.asPath() resolves the (relative) path against storage.root, so an empty
        // path resolves to the storage root itself, i.e. our temp directory.
        FileStorage storage = new FileStorage(new FileStorageId(1), null, FileStorageType.CRAWL_DATA,
                LocalDateTime.now(), "", FileStorageState.ACTIVE, "test");

        fileStorageService = mock(FileStorageService.class);
        when(fileStorageService.getStorage(new FileStorageId(1))).thenReturn(storage);

        transferService = new ExecutorFileTransferService(fileStorageService);
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("storage.root");
        try (var paths = Files.walk(storageDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    void missingFileReturns404() throws Exception {
        Context context = mockContext("no-such-file.slop.zip", "GET");

        Object body = transferService.transferFile(context);

        assertEquals("Not found", body);
        assertEquals(404, capturedResponseCode);
    }

    @Test
    void pathEscapeReturns403() throws Exception {
        Context context = mockContext("../../../etc/passwd", "GET");

        Object body = transferService.transferFile(context);

        assertEquals("Forbidden", body);
        assertEquals(403, capturedResponseCode);
    }

    @Test
    void presentFileHeadReturns200() throws Exception {
        Files.writeString(storageDir.resolve("present.slop.zip"), "hello");
        Context context = mockContext("present.slop.zip", "HEAD");

        transferService.transferFile(context);

        assertEquals(200, capturedResponseCode);
    }

    private int capturedResponseCode = -1;

    private Context mockContext(String path, String method) {
        Context context = mock(Context.class);

        Value fidValue = mock(Value.class);
        when(fidValue.value("")).thenReturn("1");
        when(context.path("fid")).thenReturn(fidValue);

        Value pathValue = mock(Value.class);
        when(pathValue.value("")).thenReturn(path);
        when(context.query("path")).thenReturn(pathValue);

        Value rangeValue = mock(Value.class);
        when(rangeValue.valueOrNull()).thenReturn(null);
        when(context.header("Range")).thenReturn(rangeValue);

        when(context.getMethod()).thenReturn(method);
        when(context.setResponseCode(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(inv -> {
            capturedResponseCode = inv.getArgument(0);
            return context;
        });
        when(context.setResponseHeader(anyString(), anyString())).thenReturn(context);
        when(context.setResponseType(anyString())).thenReturn(context);

        return context;
    }
}
