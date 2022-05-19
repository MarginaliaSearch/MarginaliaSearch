package nu.marginalia.wmsa.data_store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.prometheus.client.Histogram;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.data_store.meta.DomainInformation;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.UrlEncoded;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

@Singleton
public class EdgeDataStoreService {

    private final EdgeDataStoreDao dataStore;
    private final Gson gson = new GsonBuilder().create();


    static final Histogram request_time_metrics
            = Histogram.build("wmsa_edge_data_store_request_time", "DB Request Time")
                .labelNames("request")
                .register();

    @Inject
    public EdgeDataStoreService(EdgeDataStoreDao dataStore) {
        this.dataStore = dataStore;
    }

    @SneakyThrows
    public Object putLink(Request request, Response response) {
        final long start = System.currentTimeMillis();

        var model = readFromJson(request, EdgeDomainLink[].class);

        dataStore.putLink(false, model);

        request_time_metrics.labels("put_link").observe(System.currentTimeMillis() - start);

        response.status(HttpStatus.CREATED_201);
        return "";
    }

    @SneakyThrows
    public Object putUrl(Request request, Response response) {
        final long start = System.currentTimeMillis();

        var model = readFromJson(request, EdgeUrl[].class);

        double quality = Double.parseDouble(request.queryParams("quality"));
        dataStore.putUrl(quality, model);

        request_time_metrics.labels("put_url").observe(System.currentTimeMillis() - start);

        response.status(HttpStatus.CREATED_201);
        return "";
    }

    @SneakyThrows
    public Object putUrlVisited(Request request, Response response) {
        final long start = System.currentTimeMillis();

        var model = readFromJson(request, EdgeUrlVisit[].class);

        dataStore.putUrlVisited(model);

        request_time_metrics.labels("put_url_visited").observe(System.currentTimeMillis() - start);

        response.status(HttpStatus.CREATED_201);
        return "";
    }

    public Object putDomainAlias(Request request, Response response) {
        final long start = System.currentTimeMillis();

        var src = UrlEncoded.decodeString(request.splat()[0]);
        var dst  = UrlEncoded.decodeString(request.splat()[1]);

        dataStore.putDomainAlias(new EdgeDomain(src), new EdgeDomain(dst));

        request_time_metrics.labels("put_domain_alias").observe(System.currentTimeMillis() - start);

        response.status(HttpStatus.ACCEPTED_202);
        return "";
    }

    public Object getUrlName(Request request, Response response) {
        final long start = System.currentTimeMillis();

        try {
            var id = Integer.parseInt(request.params("id"));
            var ret = dataStore.getUrl(new EdgeId<>(id));

            request_time_metrics.labels("get_url_name").observe(System.currentTimeMillis() - start);
            return ret;
        }
        catch (NoSuchElementException ex) {
            response.status(404);
            return "";
        }

    }

    public Object getDomainId(Request request, Response response) {
        final long start = System.currentTimeMillis();

        var domain = UrlEncoded.decodeString(request.splat()[0]);

        try {
            var ret = dataStore.getDomainId(new EdgeDomain(domain));

            request_time_metrics.labels("get_domain_id").observe(System.currentTimeMillis() - start);
            return ret;
        }
        catch (NoSuchElementException ex) {
            response.status(404);
            return "";
        }

    }

    public Object getDomainName(Request request, Response response) {
        final long start = System.currentTimeMillis();

        try {
            var id = Integer.parseInt(request.params("id"));
            var ret = dataStore.getDomain(new EdgeId<>(id));

            request_time_metrics.labels("get_domain_name").observe(System.currentTimeMillis() - start);
            return ret;
        }
        catch (NoSuchElementException ex) {
            response.status(404);
            return "";
        }

    }

    public <T> T readFromJson(Request request, Class<T> clazz) throws IOException {
        if ("gzip".equals(request.headers("Content-Encoding"))) {
            return gson.fromJson(new InputStreamReader(new GZIPInputStream(request.raw().getInputStream())), clazz);
        }
        else {
            return gson.fromJson(new InputStreamReader(request.raw().getInputStream()), clazz);
        }
    }


    public DomainInformation domainInfo(Request request, Response response) throws URISyntaxException {
        final String site = request.params("site");

        EdgeId<EdgeDomain> domainId = getDomainFromPartial(site);
        if (domainId == null) {
            response.status(404);
            return null;
        }
        EdgeDomain domain = dataStore.getDomain(domainId);

        boolean blacklisted = dataStore.isBlacklisted(domain);
        int pagesKnown = dataStore.getPagesKnown(domainId);
        int pagesVisited = dataStore.getPagesVisited(domainId);
        int pagesIndexed = dataStore.getPagesIndexed(domainId);
        int incomingLinks = dataStore.getIncomingLinks(domainId);
        int outboundLinks = dataStore.getOutboundLinks(domainId);
        double rank = Math.round(10000.0*(1.0-dataStore.getRank(domainId)))/100;
        EdgeDomainIndexingState state = dataStore.getDomainState(domainId);
        double nominalQuality = Math.round(100*100*Math.exp(dataStore.getDomainQuality(domainId)))/100.;
        List<EdgeDomain> linkingDomains = dataStore.getLinkingDomains(domainId);

        return new DomainInformation(domain, blacklisted, pagesKnown, pagesVisited, pagesIndexed, incomingLinks, outboundLinks, nominalQuality, rank, state, linkingDomains);
    }

    private EdgeId<EdgeDomain> getDomainFromPartial(String site) {
        try {
            return dataStore.getDomainId(new EdgeDomain(site));
        }
        catch (Exception ex) {
            try {
                return dataStore.getDomainId(new EdgeDomain(site));
            }
            catch (Exception ex2) {
                return null;
            }
        }

    }
}
