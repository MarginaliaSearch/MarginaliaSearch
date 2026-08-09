package nu.marginalia.converting.sideload.encyclopedia;

import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.encyclopedia.cleaner.model.ArticleParts;
import nu.marginalia.encyclopedia.model.Article;
import nu.marginalia.encyclopedia.model.Link;
import nu.marginalia.encyclopedia.model.LinkList;
import nu.marginalia.encyclopedia.store.ArticleDbProvider;
import nu.marginalia.encyclopedia.store.ArticleStoreWriter;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.model.idx.DocumentMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

class EncyclopediaSideloaderTopologyTest {

    @Test
    public void testIncomingLinkCountsBecomeTopology() throws Exception {
        Path dbFile = Files.createTempFile(getClass().getSimpleName(), ".db");
        try {
            try (ArticleStoreWriter writer = new ArticleStoreWriter(new ArticleDbProvider(dbFile))) {
                writer.add(new Article("Napoleon", "Napoleon", "The emperor",
                        new ArticleParts("Napoleon was an emperor"),
                        new LinkList(new Link("France", "France"), new Link("Waterloo%2C_Belgium", "Waterloo")),
                        new LinkList()).asData());
                writer.add(new Article("France", "France", "The country",
                        new ArticleParts("France is a country"),
                        new LinkList(new Link("Napoleon", "Napoleon")),
                        new LinkList()).asData());
                writer.add(new Article("Waterloo,_Belgium", "Waterloo, Belgium", "The town",
                        new ArticleParts("Waterloo is a town"),
                        new LinkList(new Link("Napoleon", "the emperor"), new Link("France", "France")),
                        new LinkList()).asData());
            }

            var anchorTagsSource = Mockito.mock(AnchorTagsSource.class);
            Mockito.when(anchorTagsSource.getAnchorTags(anyString())).thenReturn(new DomainLinks());
            var anchorTagsSourceFactory = Mockito.mock(AnchorTagsSourceFactory.class);
            Mockito.when(anchorTagsSourceFactory.create(anyList())).thenReturn(anchorTagsSource);

            var anchorTextKeywords = Mockito.mock(AnchorTextKeywords.class);

            var sideloaderProcessing = Mockito.mock(SideloaderProcessing.class);
            Mockito.when(sideloaderProcessing.processDocument(
                            anyString(), anyString(), anyList(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> {
                        var doc = new ProcessedDocument();
                        doc.url = new EdgeUrl(invocation.getArgument(0, String.class));
                        doc.details = new ProcessedDocumentDetails();
                        doc.details.metadata = new DocumentMetadata(0, 0, 0, 0, 0, 0, 0, (byte) 0);
                        return doc;
                    });

            var sideloader = new EncyclopediaMarginaliaNuSideloader(
                    dbFile, "https://en.wikipedia.org/wiki/", GsonFactory.get(),
                    anchorTagsSourceFactory, anchorTextKeywords, sideloaderProcessing);

            Map<String, Integer> topologyByUrl = new HashMap<>();
            var iter = sideloader.getDocumentsStream();
            while (iter.hasNext()) {
                ProcessedDocument doc = iter.next();
                topologyByUrl.put(doc.url.path, doc.details.metadata.topology());
            }

            assertEquals(3, topologyByUrl.size());
            assertEquals(2, topologyByUrl.get("/wiki/Napoleon"));
            assertEquals(2, topologyByUrl.get("/wiki/France"));
            assertEquals(1, topologyByUrl.get("/wiki/Waterloo,_Belgium"));

            sideloader.close();
        }
        finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
