package nu.marginalia.util.ranking.tool;

import lombok.SneakyThrows;
import nu.marginalia.util.ranking.AcademiaRank;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import org.mariadb.jdbc.Driver;

import java.io.IOException;

public class TestAcademiaRankTool {

    @SneakyThrows
    public static void main(String... args) throws IOException {
        Driver driver = new Driver();
        var conn = new DatabaseModule().provideConnection();

        var rank = new AcademiaRank(new DatabaseModule().provideConnection(), "www.perseus.tufts.edu", "xroads.virginia.edu");
        var res = rank.getResult();

        try (var c = conn.getConnection(); var stmt = c.prepareStatement("SELECT URL_PART FROM EC_DOMAIN WHERE ID=?")) {
            for (int i = 0; i < Math.min(res.size(), 100); i++) {
                stmt.setInt(1, res.getQuick(i));
                var rsp = stmt.executeQuery();
                while (rsp.next())
                    System.out.println(rsp.getString(1));
            }
        }
    }

}
