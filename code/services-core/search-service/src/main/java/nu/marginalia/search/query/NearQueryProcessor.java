package nu.marginalia.search.query;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NearQueryProcessor {

    private final HikariDataSource dataSource;

    @Inject
    public NearQueryProcessor(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SneakyThrows
    public List<Integer> getRelatedDomains(String term, Consumer<String> onProblem) {
        List<Integer> ret = new ArrayList<>();
        try (var conn = dataSource.getConnection();

             var selfStmt = conn.prepareStatement("""
                     SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?
                     """);
             var stmt = conn.prepareStatement("""
                     SELECT NEIGHBOR_ID, ND.INDEXED, ND.STATE FROM EC_DOMAIN_NEIGHBORS_2
                                          INNER JOIN EC_DOMAIN ND ON ND.ID=NEIGHBOR_ID
                                          WHERE DOMAIN_ID=?
                     """)) {
            ResultSet rsp;
            selfStmt.setString(1, term);
            rsp = selfStmt.executeQuery();
            int domainId = -1;
            if (rsp.next()) {
                domainId = rsp.getInt(1);
                ret.add(domainId);
            }

            stmt.setInt(1, domainId);
            rsp = stmt.executeQuery();

            while (rsp.next()) {
                int id = rsp.getInt(1);
                int indexed = rsp.getInt(2);
                String state = rsp.getString(3);

                if (indexed > 0 && ("ACTIVE".equalsIgnoreCase(state) || "SOCIAL_MEDIA".equalsIgnoreCase(state) || "SPECIAL".equalsIgnoreCase(state))) {
                    ret.add(id);
                }
            }

        }

        if (ret.isEmpty()) {
            onProblem.accept("Could not find domains adjacent " + term);
        }

        return ret;
    }

}
