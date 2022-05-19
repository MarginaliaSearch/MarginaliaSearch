package nu.marginalia.wmsa.edge.tools;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.mariadb.jdbc.Driver;

import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DomainInserterMain {
    public static void main(String... args) throws Exception {
        org.mariadb.jdbc.Driver driver = new Driver();

        var conn = new DatabaseModule().provideConnection();

        var dao = new EdgeDataStoreDaoImpl(conn);
        Set<EdgeDomain> domains = new HashSet<>();

        var connection = conn.getConnection();

        try (var br = Files.newBufferedReader(Path.of(args[0]));
             var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE URL_PART=?");
             var setRankStmt = connection.prepareStatement("UPDATE EC_DOMAIN SET RANK=? WHERE URL_PART=?")
        ) {
            String line;

            List<EdgeUrl> loadUrls = new ArrayList<>(100);

            for (;;) {
                loadUrls.clear();

                double quality;

                line = br.readLine();
                if (null == line) break;
                quality = Double.parseDouble(line);

                line = br.readLine();
                if (null == line) break;
                var url = getUrl(line);
                stmt.setString(1, url.domain.toString());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    System.out.println("Known: " + line);
                    while (null != (line = br.readLine()) && !line.isBlank()) {
                        if (".".equals(line)) break;
                    }
                    setRankStmt.setString(2, url.getDomain().toString());
                    setRankStmt.setDouble(1, quality);
                    setRankStmt.executeUpdate();
                }
                else {
                    loadUrls.add(url);
                    while (null != (line = br.readLine()) && !line.isBlank()) {
                        if (".".equals(line)) break;
                        loadUrls.add(getUrl(line));
                    }

                    dao.putUrl(-2*quality, loadUrls.toArray(EdgeUrl[]::new));

                    System.out.println(loadUrls);
                }
            }
        }
    }

    @SneakyThrows
    static EdgeUrl getUrl(String line) {
        String[] parts = line.split("/", 4);
        return new EdgeUrl(parts[0]+"//"+parts[2]+"/" + URLEncoder.encode(parts[3]));
    }


}
