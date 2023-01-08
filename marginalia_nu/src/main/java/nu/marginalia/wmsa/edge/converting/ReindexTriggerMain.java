package nu.marginalia.wmsa.edge.converting;

import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;


public class ReindexTriggerMain {
    private static final Logger logger = LoggerFactory.getLogger(ReindexTriggerMain.class);

    public static void main(String... args) throws IOException, SQLException {
        var db = new DatabaseModule();
        var client = new OkHttpClient.Builder()
                .connectTimeout(100, TimeUnit.MILLISECONDS)
                .readTimeout(15, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();

        logger.info("Updating statistics");
        var updateStatistics = new UpdateDomainStatistics(db.provideConnection());
        updateStatistics.run();

        var rb = new RequestBody() {

            @Nullable
            @Override
            public MediaType contentType() {
                return MediaType.parse("text/plain");
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.writeString("NOOP", Charset.defaultCharset());
            }
        };

        logger.info("Repartitioning");
        client.newCall(new Request.Builder().post(rb).url(new URL("http", args[0], ServiceDescriptor.EDGE_INDEX.port, "/ops/repartition")).build()).execute();
        logger.info("Reindexing");
        client.newCall(new Request.Builder().post(rb).url(new URL("http", args[0], ServiceDescriptor.EDGE_INDEX.port, "/ops/reindex")).build()).execute();

    }


}
