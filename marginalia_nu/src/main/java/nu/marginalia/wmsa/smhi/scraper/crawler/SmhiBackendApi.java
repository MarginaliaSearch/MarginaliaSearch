package nu.marginalia.wmsa.smhi.scraper.crawler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.smhi.model.Plats;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

@Singleton
public class SmhiBackendApi {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String server = "https://opendata-download-metfcst.smhi.se/api";
    private final PoolingHttpClientConnectionManager connectionManager;
    private final String userAgent;

    @Inject
    public SmhiBackendApi(@Named("smhi-user-agent") String userAgent) {
        this.userAgent = userAgent;

        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);
        connectionManager.setDefaultMaxPerRoute(20);
        HttpHost host = new HttpHost("https://opendata-download-metfcst.smhi.se");
        connectionManager.setMaxPerRoute(new HttpRoute(host), 50);
    }

    public SmhiApiRespons hamtaData(Plats plats) throws Exception {
        var client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        String url = String.format(Locale.US, "%s/category/pmp3g/version/2/geotype/point/lon/%f/lat/%f/data.json",
                server, plats.longitud, plats.latitud);

        Thread.sleep(100);

        logger.info("Fetching {} - {}", plats, url);

        HttpGet get = new HttpGet(url);
        get.addHeader("User-Agent", userAgent);

        try (var rsp = client.execute(get)) {
            var entity = rsp.getEntity();
            String content = new String(entity.getContent().readAllBytes());
            int statusCode = rsp.getStatusLine().getStatusCode();

            var expires =
                    Arrays.stream(rsp.getHeaders("Expires"))
                            .map(Header::getValue)
                            .map(DateTimeFormatter.RFC_1123_DATE_TIME::parse)
                            .map(LocalDateTime::from)
                            .findFirst().map(Object::toString).orElse("");


            if (statusCode == 200) {
                return new SmhiApiRespons(content, expires, plats);
            }
            throw new IllegalStateException("Fel i backend " + statusCode + " " + content);
        }

    }

}

class SmhiApiRespons {
    public final String jsonContent;
    public final String expiryDate;
    public final Plats plats;

    SmhiApiRespons(String jsonContent, String expiryDate, Plats plats) {
        this.jsonContent = jsonContent;
        this.expiryDate = expiryDate;
        this.plats = plats;
    }
}