package nu.marginalia.wmsa.edge.tools;

import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.dict.WikiCleaner;
import nu.marginalia.wmsa.encyclopedia.EncyclopediaClient;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.io.IOException;
import java.util.concurrent.*;

public class EncyclopediaLoaderTool {

    static final EncyclopediaClient encyclopediaClient = new EncyclopediaClient();

    public static void main(String[] args) throws IOException, InterruptedException {
        convertAll(args);
        encyclopediaClient.close();
        System.exit(0);
    }

    private static void convertAll(String[] args) throws IOException, InterruptedException {
        var zr = new ZIMReader(new ZIMFile(args[0]));

        var pool = Executors.newFixedThreadPool(8);
        var sem = new Semaphore(12);
        zr.forEachArticles((url, art) -> {
           if (art != null) {
               try {
                   sem.acquire();

                   pool.execute(() -> {
                       try {
                           convert(url, art);
                       } finally {
                           sem.release();
                       }
                   });
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }
           }
       }, p -> true);

        sem.acquire(12);

        encyclopediaClient.close();
    }

    private static void convert(String url, String art) {
        String newData = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/" + url, art);

        if (null != newData) {
            encyclopediaClient.submitWiki(Context.internal(), url, newData)
                    .retry(5)
                    .blockingSubscribe();
        }
    }
}
