package nu.marginalia.wmsa.edge.tools;

import com.github.luben.zstd.Zstd;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.assistant.dict.WikiCleaner;
import org.mariadb.jdbc.Driver;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EncyclopediaLoaderTool extends ParallelPipe<EncyclopediaLoaderTool.ArticleRaw, EncyclopediaLoaderTool.ArticleProcessed> implements AutoCloseable {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        org.mariadb.jdbc.Driver driver = new Driver();

        try (var loader = new EncyclopediaLoaderTool(new DatabaseModule().provideConnection())) {
            var zr = new ZIMReader(new ZIMFile(args[0]));

            zr.forEachArticles((url, art) -> {
                if (art != null) {
                    loader.accept(new ArticleRaw(url, art));
                }
            }, p->true);

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        System.exit(0);
    }

    public record ArticleRaw(String url, String art) {
        public ArticleProcessed toProcessed(String data) {
            return new ArticleProcessed(url, data);
        }
    }
    public record ArticleProcessed(String url, String art) {}


    private final HikariDataSource dataSource;
    private final Connection connection;
    private final PreparedStatement insertArticleDataStatement;

    private final WikiCleaner wikiCleaner = new WikiCleaner();

    public EncyclopediaLoaderTool(HikariDataSource dataSource) throws SQLException {
        super("EncyclopediaPipe", 24, 4, 2);
        this.dataSource = dataSource;
        this.connection = dataSource.getConnection();
        this.insertArticleDataStatement = connection.prepareStatement("REPLACE INTO REF_WIKI_ARTICLE(NAME, ENTRY) VALUES (?, ?)");

    }

    @Override
    protected ArticleProcessed onProcess(ArticleRaw articleRaw) {
        return articleRaw.toProcessed(wikiCleaner.cleanWikiJunk("https://en.wikipedia.org/wiki/" + articleRaw.url, articleRaw.art));
    }

    @Override
    protected void onReceive(ArticleProcessed articleProcessed) throws Exception {
        if (articleProcessed.art == null) return;

        try (var bs = new ByteArrayInputStream(Zstd.compress(articleProcessed.art.getBytes(StandardCharsets.UTF_8)))) {
            insertArticleDataStatement.setString(1, articleProcessed.url);
            insertArticleDataStatement.setBlob(2, bs);
            insertArticleDataStatement.executeUpdate();
        }
    }

    public void close() throws Exception {
        join();
        if (insertArticleDataStatement != null) insertArticleDataStatement.close();
        if (connection != null) connection.close();
        if (dataSource != null) dataSource.close();
    }
}
