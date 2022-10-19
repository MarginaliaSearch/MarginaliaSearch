package nu.marginalia.wmsa.edge.converting;

import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static nu.marginalia.wmsa.edge.index.EdgeIndexService.DYNAMIC_BUCKET_LENGTH;

public class ReindexTriggerMain {

    public static void main(String... args) throws IOException, SQLException {
        var db = new DatabaseModule();
        var client = new OkHttpClient.Builder()
                .connectTimeout(100, TimeUnit.MILLISECONDS)
                .readTimeout(15, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();

        try (var ds =  db.provideConnection(); var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT ID, DOMAIN_NAME, STATE, INDEXED FROM EC_DOMAIN LIMIT 100");
            while (rs.next()) {
                System.out.printf("%d %s %s %d\n",
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4));
            }

            rs = stmt.executeQuery("SELECT ID, DOMAIN_ID, PATH, VISITED, STATE FROM EC_URL LIMIT 100");
            while (rs.next()) {
                System.out.printf("%d %d %s %d %s\n",
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getString(5));

            }

            stmt.executeUpdate("INSERT IGNORE INTO DOMAIN_METADATA(ID,GOOD_URLS,KNOWN_URLS,VISITED_URLS) SELECT ID,0,0,0 FROM EC_DOMAIN WHERE INDEXED>0");
            stmt.executeUpdate("UPDATE DOMAIN_METADATA INNER JOIN (SELECT DOMAIN_ID,COUNT(*) CNT FROM EC_URL WHERE VISITED AND STATE='ok' GROUP BY DOMAIN_ID) T ON T.DOMAIN_ID=ID SET GOOD_URLS=CNT");
            stmt.executeUpdate("UPDATE DOMAIN_METADATA INNER JOIN (SELECT DOMAIN_ID,COUNT(*) CNT FROM EC_URL GROUP BY DOMAIN_ID) T ON T.DOMAIN_ID=ID SET KNOWN_URLS=CNT");
            stmt.executeUpdate("UPDATE DOMAIN_METADATA INNER JOIN (SELECT DOMAIN_ID,COUNT(*) CNT FROM EC_URL WHERE VISITED GROUP BY DOMAIN_ID) T ON T.DOMAIN_ID=ID SET VISITED_URLS=CNT");
        }

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

        client.newCall(new Request.Builder().post(rb).url(new URL("http", args[0], ServiceDescriptor.EDGE_INDEX.port, "/ops/repartition")).build()).execute();

        if (!Boolean.getBoolean("no-preconvert")) {
            client.newCall(new Request.Builder().post(rb).url(new URL("http", args[0], ServiceDescriptor.EDGE_INDEX.port, "/ops/preconvert")).build()).execute();
        }

        for (int i = 0; i < DYNAMIC_BUCKET_LENGTH+1; i++) {
            client.newCall(new Request.Builder().post(rb).url(new URL("http", args[0], ServiceDescriptor.EDGE_INDEX.port, "/ops/reindex/" + i)).build()).execute();
        }

    }


}
