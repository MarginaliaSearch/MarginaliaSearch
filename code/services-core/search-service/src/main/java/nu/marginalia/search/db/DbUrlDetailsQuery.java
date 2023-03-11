package nu.marginalia.search.db;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.id.EdgeId;
import nu.marginalia.model.id.EdgeIdCollection;
import nu.marginalia.search.model.PageScoreAdjustment;
import nu.marginalia.search.model.UrlDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class DbUrlDetailsQuery {
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cache<EdgeUrl, EdgeId<EdgeUrl>> urlIdCache = CacheBuilder.newBuilder().maximumSize(100_000).build();

    public static double QUALITY_LOWER_BOUND_CUTOFF = -15.;
    @Inject
    public DbUrlDetailsQuery(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    public synchronized void clearCaches()
    {
        urlIdCache.invalidateAll();
    }

    private <T> String idList(EdgeIdCollection<EdgeUrl> ids) {
        StringJoiner j = new StringJoiner(",", "(", ")");
        for (var id : ids.values()) {
            j.add(Integer.toString(id));
        }
        return j.toString();
    }

    @SneakyThrows
    public List<UrlDetails> getUrlDetailsMulti(EdgeIdCollection<EdgeUrl> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<UrlDetails> result = new ArrayList<>(ids.size());

        try (var connection = dataSource.getConnection()) {

            String idString = idList(ids);

            try (var stmt = connection.prepareStatement(
                    """
                            SELECT ID, DOMAIN_ID, URL,
                                    TITLE, DESCRIPTION,
                                    QUALITY,
                                    WORDS_TOTAL, FORMAT, FEATURES,
                                    IP, DOMAIN_STATE,
                                    DATA_HASH
                                    FROM EC_URL_VIEW
                                    WHERE TITLE IS NOT NULL 
                                    AND ID IN
                            """ + idString)) {
                stmt.setFetchSize(ids.size());

                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    var val = new UrlDetails(rsp.getInt(1),
                            rsp.getInt(2),
                            new EdgeUrl(rsp.getString(3)),
                            rsp.getString(4), // title
                            rsp.getString(5), // description
                            rsp.getDouble(6), // quality
                            rsp.getInt(7), // wordsTotal
                            rsp.getString(8), // format
                            rsp.getInt(9), // features
                            rsp.getString(10), // ip
                            DomainIndexingState.valueOf(rsp.getString(11)), // domainState
                            rsp.getLong(12), // dataHash
                            PageScoreAdjustment.zero(), // urlQualityAdjustment
                            Integer.MAX_VALUE, // rankingId
                            Double.MAX_VALUE, // termScore
                            1, // resultsFromSameDomain
                            "", // positions
                            null // result item
                            );
                    if (val.urlQuality <= QUALITY_LOWER_BOUND_CUTOFF
                    && Strings.isNullOrEmpty(val.description)
                    && val.url.path.length() > 1) {
                        continue;
                    }
                    result.add(val);

                }
            }
        }

        return result;
    }




}
