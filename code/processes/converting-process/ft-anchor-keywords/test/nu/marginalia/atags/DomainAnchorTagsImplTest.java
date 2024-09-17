package nu.marginalia.atags;

import nu.marginalia.atags.source.AnchorTagsImpl;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.util.TestLanguageModels;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

class DomainAnchorTagsImplTest {

    @Test
    void getAnchorTags() {
        Path atagsPath = Path.of("/home/vlofgren/atags.parquet");

        if (!Files.exists(atagsPath)) {
            // Not really practical to ship a multi-gb file in the git repo
            // atags.parquet is available at https://downloads.marginalia.nu/exports

            return;  // skip test
        }

        try (var domainAnchorTags = new AnchorTagsImpl(
                atagsPath, List.of(new EdgeDomain("www.chiark.greenend.org.uk"))
        )) {
            var tags = domainAnchorTags.getAnchorTags(new EdgeDomain("www.chiark.greenend.org.uk"));

            System.out.println(tags);
            System.out.println(tags.getUrls("http"));
            System.out.println(tags.forUrl(new EdgeUrl("https://www.chiark.greenend.org.uk/~sgtatham/putty/")));
            System.out.println(tags.forUrl(new EdgeUrl("http://www.chiark.greenend.org.uk/~sgtatham/putty/")));
            System.out.println(tags.forUrl(new EdgeUrl("http://www.chiark.greenend.org.uk/~sgtatham/putt")));

            var atagsKeywords = new AnchorTextKeywords(
                    new SentenceExtractor(
                            TestLanguageModels.getLanguageModels()
                    )
            );
            System.out.println(
                    atagsKeywords.getAnchorTextKeywords(tags, new EdgeUrl("https://www.chiark.greenend.org.uk/~sgtatham/"))
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}