package nu.marginalia.svc;

import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import spark.Spark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.mockito.Mockito.when;

class ExecutorFileTransferServiceTest {

    @Test
    public void test() throws SQLException, InterruptedException {
        // Test requires this file to exist
        if (!Files.exists(Path.of("/tmp/crawl.parquet"))) {
            return;
        }

        var fileStorage = Mockito.mock(FileStorageService.class);

        when(fileStorage.getStorage(Mockito.any(FileStorageId.class))).thenReturn(new FileStorage(null,
                        null,
                        null,
                        null,
                        "/tmp",
                        null,
                        null));

        var svc = new ExecutorFileTransferService(fileStorage);

        Spark.port(9998);
        Spark.get("/transfer/file/:fid", svc::transferFile);
        Spark.head("/transfer/file/:fid", svc::transferFile);

        Spark.init();

        Thread.sleep(1000);


        try (var conn = DriverManager.getConnection("jdbc:duckdb:");
             var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("""
                SELECT COUNT(*) AS cnt, httpStatus 
                FROM 'http://localhost:9998/transfer/file/0?path=crawl.parquet' 
                GROUP BY httpStatus
                """);
            while (rs.next()) {
                System.out.println(rs.getInt("CNT") + " " + rs.getInt("httpStatus"));
            }
        }

        Spark.stop();
    }
}