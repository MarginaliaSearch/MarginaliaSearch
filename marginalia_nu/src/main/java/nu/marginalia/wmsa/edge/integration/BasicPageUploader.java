package nu.marginalia.wmsa.edge.integration;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlFeature;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;

import java.util.EnumSet;

public class BasicPageUploader {
    private final EdgeDataStoreDao edgeStoreDao;
    private final EdgeIndexClient indexClient;

    private final int features;

    @Inject
    public BasicPageUploader(EdgeDataStoreDao edgeStoreDao, EdgeIndexClient indexClient,
                             EnumSet<HtmlFeature> features) {

        this.edgeStoreDao = edgeStoreDao;
        this.indexClient = indexClient;
        this.features = HtmlFeature.encode(features);

    }

    public void upload(BasicDocumentData indexData) {
        var url = indexData.getUrl();

        edgeStoreDao.putUrl(-2, url);
        edgeStoreDao.putUrlVisited(new EdgeUrlVisit(url, indexData.getHashCode(), -2.,
                indexData.getTitle(),
                indexData.getDescription()
                , "",
                EdgeHtmlStandard.HTML5.toString(),
                features,
                indexData.wordCount, indexData.wordCount, EdgeUrlState.OK));
        edgeStoreDao.putLink(false, indexData.domainLinks);

        putWords(edgeStoreDao.getDomainId(url.domain).getId(),
                edgeStoreDao.getUrlId(url).getId(),
                -2,
                indexData.words);
    }

    void putWords(int didx, int idx, double quality, EdgePageWordSet wordsSet) {
        indexClient.putWords(Context.internal(), new EdgeId<>(didx), new EdgeId<>(idx), quality,
                wordsSet, 0).blockingSubscribe();
    }

}
