package nu.marginalia.search.svc;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SimilarDomainsService {

    private static final Logger logger = LoggerFactory.getLogger(SimilarDomainsService.class);
    private final HikariDataSource dataSource;

    @Inject
    public SimilarDomainsService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<SimilarDomain> getSimilarDomains(int domainId, int count) {
        // Tell me you've worked in enterprise software without telling me you've worked in enterprise software
        String q1 = """
                    SELECT
                        NEIGHBOR.ID AS ID,
                        NEIGHBOR.DOMAIN_NAME AS DOMAIN_NAME,
                        SCREENSHOT.DOMAIN_NAME IS NOT NULL AS HAS_SCREENSHOT,
                        NODE_AFFINITY > 0 AS INDEXED,
                        STATE='ACTIVE' AS ACTIVE,
                        RELATEDNESS,
                        RANK,
                        STOD.ID IS NOT NULL AS LINK_STOD,
                        DTOS.ID IS NOT NULL AS LINK_DTOS
                    FROM EC_DOMAIN_NEIGHBORS_2
                    INNER JOIN EC_DOMAIN AS NEIGHBOR ON EC_DOMAIN_NEIGHBORS_2.NEIGHBOR_ID = NEIGHBOR.ID
                    LEFT JOIN DATA_DOMAIN_SCREENSHOT AS SCREENSHOT ON NEIGHBOR.DOMAIN_NAME = SCREENSHOT.DOMAIN_NAME
                    LEFT JOIN EC_DOMAIN_LINK STOD ON STOD.SOURCE_DOMAIN_ID = NEIGHBOR.ID AND STOD.DEST_DOMAIN_ID =   EC_DOMAIN_NEIGHBORS_2.DOMAIN_ID
                    LEFT JOIN EC_DOMAIN_LINK DTOS ON DTOS.DEST_DOMAIN_ID   = NEIGHBOR.ID AND DTOS.SOURCE_DOMAIN_ID = EC_DOMAIN_NEIGHBORS_2.DOMAIN_ID
                    WHERE DOMAIN_ID = ?
                    ORDER BY RELATEDNESS DESC, RANK ASC
                    LIMIT ?
                    """;
        String q2 = """
                    SELECT
                        NEIGHBOR.ID AS ID,
                        NEIGHBOR.DOMAIN_NAME AS DOMAIN_NAME,
                        SCREENSHOT.DOMAIN_NAME IS NOT NULL AS HAS_SCREENSHOT,
                        NODE_AFFINITY > 0 AS INDEXED,
                        STATE='ACTIVE' AS ACTIVE,
                        RELATEDNESS,
                        RANK,
                        STOD.ID IS NOT NULL AS LINK_STOD,
                        DTOS.ID IS NOT NULL AS LINK_DTOS
                    FROM EC_DOMAIN_NEIGHBORS_2
                    INNER JOIN EC_DOMAIN AS NEIGHBOR ON EC_DOMAIN_NEIGHBORS_2.DOMAIN_ID = NEIGHBOR.ID
                    LEFT JOIN DATA_DOMAIN_SCREENSHOT AS SCREENSHOT ON NEIGHBOR.DOMAIN_NAME = SCREENSHOT.DOMAIN_NAME
                    LEFT JOIN EC_DOMAIN_LINK STOD ON STOD.SOURCE_DOMAIN_ID = NEIGHBOR.ID AND STOD.DEST_DOMAIN_ID = EC_DOMAIN_NEIGHBORS_2.NEIGHBOR_ID
                    LEFT JOIN EC_DOMAIN_LINK DTOS ON DTOS.DEST_DOMAIN_ID = NEIGHBOR.ID AND DTOS.SOURCE_DOMAIN_ID = EC_DOMAIN_NEIGHBORS_2.NEIGHBOR_ID
                    WHERE NEIGHBOR_ID = ?
                    ORDER BY RELATEDNESS DESC, RANK ASC
                    LIMIT ?
            """;

        var domains = executeSimilarDomainsQueries(domainId, count, q1, q2);

        domains.removeIf(d -> d.url.domain.toString().length() > 32);

        domains.sort(Comparator.comparing(SimilarDomain::relatedness).reversed().thenComparing(SimilarDomain::domainId));

        return domains;
    }

    private TIntSet getLinkingIdsDToS(int domainId) {
        String idQuery = """
                SELECT DEST_DOMAIN_ID AS ID FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=?
                """;

        TIntSet ids = new TIntHashSet();

        try (var connection = dataSource.getConnection()) {
            try (var stmt1 = connection.prepareStatement(idQuery)) {

                stmt1.setInt(1, domainId);
                var rsp = stmt1.executeQuery();

                while (rsp.next()) {
                    ids.add(rsp.getInt(1));
                }
            }
        }
        catch (SQLException throwables) {
            logger.warn("Failed to get domain neighbors for domain", throwables);
        }
        return ids;
    }
    private TIntSet getLinkingIdsSToD(int domainId) {
        String idQuery = """
                SELECT SOURCE_DOMAIN_ID AS ID FROM EC_DOMAIN_LINK WHERE DEST_DOMAIN_ID=?
                """;

        TIntSet ids = new TIntHashSet();

        try (var connection = dataSource.getConnection()) {
            try (var stmt1 = connection.prepareStatement(idQuery)) {

                stmt1.setInt(1, domainId);
                var rsp = stmt1.executeQuery();

                while (rsp.next()) {
                    ids.add(rsp.getInt(1));
                }
            }
        }
        catch (SQLException throwables) {
            logger.warn("Failed to get domain neighbors for domain", throwables);
        }
        return ids;
    }

    private TIntDoubleMap getRelatedness(int selfId, TIntSet ids) {
        String idQuery = """
            SELECT RELATEDNESS FROM WMSA_prod.EC_DOMAIN_NEIGHBORS_2 WHERE DOMAIN_ID=? AND NEIGHBOR_ID=?
            """;

        TIntDoubleMap ret = new TIntDoubleHashMap(ids.size());

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(idQuery)) {
                for (var id : ids.toArray()) {
                    if (selfId > id) {
                        stmt.setInt(1, selfId);
                        stmt.setInt(2, id);
                    }
                    else {
                        stmt.setInt(1, id);
                        stmt.setInt(2, selfId);
                    }

                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        double relatedness = rsp.getDouble(1);
                        ret.put(id, relatedness);
                    }

                }
            }
        }
        catch (SQLException throwables) {
            logger.warn("Failed to get domain neighbors for domain", throwables);
        }

        return ret;
    }

    public List<SimilarDomain> getLinkingDomains(int domainId, int count) {
        TIntSet linkingIdsDtoS = getLinkingIdsDToS(domainId);
        TIntSet linkingIdsStoD = getLinkingIdsSToD(domainId);

        TIntSet allIds = new TIntHashSet(linkingIdsDtoS.size() + linkingIdsStoD.size());
        allIds.addAll(linkingIdsDtoS);
        allIds.addAll(linkingIdsStoD);

        TIntDoubleMap relatedness = getRelatedness(domainId, allIds);

        List<SimilarDomain> domains = new ArrayList();

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("""
                SELECT EC_DOMAIN.DOMAIN_NAME,
                       SCREENSHOT.DOMAIN_NAME IS NOT NULL AS HAS_SCREENSHOT,
                       NODE_AFFINITY > 0 AS INDEXED,
                       STATE='ACTIVE' AS ACTIVE,
                       RANK
                FROM EC_DOMAIN
                LEFT JOIN DATA_DOMAIN_SCREENSHOT AS SCREENSHOT
                     ON EC_DOMAIN.DOMAIN_NAME = SCREENSHOT.DOMAIN_NAME
                WHERE ID=?
            """)) {
                for (int id : allIds.toArray()) {
                    stmt.setInt(1, id);
                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        domains.add(new SimilarDomain(
                                new EdgeDomain(rsp.getString("DOMAIN_NAME")).toRootUrl(),
                                id,
                                Math.round(100 * relatedness.get(id)),
                                Math.round(100 * (1. - rsp.getDouble("RANK"))),
                                rsp.getBoolean("INDEXED"),
                                rsp.getBoolean("ACTIVE"),
                                rsp.getBoolean("HAS_SCREENSHOT"),
                                LinkType.find(
                                        linkingIdsStoD.contains(id),
                                        linkingIdsDtoS.contains(id)
                                )
                        ));
                    }
                }
            }
        }
        catch (SQLException throwables) {
            logger.warn("Failed to get domain neighbors for domain", throwables);
        }

        domains.removeIf(d -> d.url.domain.toString().length() > 32);

        domains.sort(Comparator.comparing(SimilarDomain::rank)
                .thenComparing(SimilarDomain::relatedness)
                .thenComparing(SimilarDomain::indexed).reversed()
                .thenComparing(SimilarDomain::domainId));
        if (domains.size() > count)
            domains.subList(count, domains.size()).clear();

        return domains;
    }

    private List<SimilarDomain> executeSimilarDomainsQueries(int domainId, int count, String... queries) {
        List<SimilarDomain> domains = new ArrayList<>(count);
        TIntHashSet seen = new TIntHashSet();

        try (var connection = dataSource.getConnection()) {

            for (var query : queries) {
                try (var stmt = connection.prepareStatement(query)) {
                    stmt.setFetchSize(count);
                    stmt.setInt(1, domainId);
                    stmt.setInt(2, count);
                    var rsp = stmt.executeQuery();
                    while (rsp.next() && domains.size() < count * 2) {
                        int id = rsp.getInt("ID");

                        if (seen.add(id)) {
                            boolean linkStod = rsp.getBoolean("LINK_STOD");
                            boolean linkDtos = rsp.getBoolean("LINK_DTOS");
                            LinkType linkType = LinkType.find(linkStod, linkDtos);

                            domains.add(new SimilarDomain(
                                    new EdgeDomain(rsp.getString("DOMAIN_NAME")).toRootUrl(),
                                    id,
                                    Math.round(100 * rsp.getDouble("RELATEDNESS")),
                                    Math.round(100 * (1. - rsp.getDouble("RANK"))),
                                    rsp.getBoolean("INDEXED"),
                                    rsp.getBoolean("ACTIVE"),
                                    rsp.getBoolean("HAS_SCREENSHOT"),
                                    linkType
                            ));
                        }
                    }
                }
            }
        } catch (SQLException throwables) {
            logger.warn("Failed to get domain neighbors for domain", throwables);
        }

        return domains;
    }

    public record SimilarDomain(EdgeUrl url,
                                int domainId,
                                double relatedness,
                                double rank,
                                boolean indexed,
                                boolean active,
                                boolean screenshot,
                                LinkType linkType)
    {

        public String getRankSymbols() {
            if (rank > 90) {
                return "&#9733;&#9733;&#9733;&#9733;&#9733;";
            }
            if (rank > 70) {
                return "&#9733;&#9733;&#9733;&#9733;";
            }
            if (rank > 50) {
                return "&#9733;&#9733;&#9733;";
            }
            if (rank > 30) {
                return "&#9733;&#9733;";
            }
            if (rank > 10) {
                return "&#9733;";
            }
            return "";
        }
    }

    enum LinkType {
        BACKWARD,
        FOWARD,
        BIDIRECTIONAL,
        NONE;

        public static LinkType find(boolean linkStod,
                                    boolean linkDtos)
        {
            if (linkDtos && linkStod)
                return BIDIRECTIONAL;
            if (linkDtos)
                return FOWARD;
            if (linkStod)
                return BACKWARD;

            return NONE;
        }

        public String toString() {
            return switch (this) {
                case FOWARD -> "&#8594;";
                case BACKWARD -> "&#8592;";
                case BIDIRECTIONAL -> "&#8646;";
                case NONE -> "-";
            };
        }

        public String getDescription() {
            return switch (this) {
                case BACKWARD -> "Backward Link";
                case FOWARD -> "Forward Link";
                case BIDIRECTIONAL -> "Mutual Link";
                case NONE -> "No Link";
            };
        }
    };

}