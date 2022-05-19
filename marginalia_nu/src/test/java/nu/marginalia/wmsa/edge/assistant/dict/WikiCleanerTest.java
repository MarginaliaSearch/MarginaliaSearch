package nu.marginalia.wmsa.edge.assistant.dict;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class WikiCleanerTest {

    @Test
    void cleanWikiJunk() throws IOException {
        String str = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/Scamander", new String(Files.readAllBytes(Path.of("/home/vlofgren/Work/wiki-cleaner/Scamander.wiki.html"))));
        String str2 = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/Plato", new String(Files.readAllBytes(Path.of("/home/vlofgren/Work/wiki-cleaner/Plato.wiki.html"))));
        String str3 = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/C++", new String(Files.readAllBytes(Path.of("/home/vlofgren/Work/wiki-cleaner/Cpp.wiki.html"))));
        String str4 = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/Memex", new String(Files.readAllBytes(Path.of("/home/vlofgren/Work/wiki-cleaner/Memex.wiki.html"))));
        Files.writeString(Path.of("/home/vlofgren/Work/wiki-cleaner/Scamander.out.html"), str);
        Files.writeString(Path.of("/home/vlofgren/Work/wiki-cleaner/Plato.out.html"), str2);
        Files.writeString(Path.of("/home/vlofgren/Work/wiki-cleaner/Cpp.out.html"), str3);
        Files.writeString(Path.of("/home/vlofgren/Work/wiki-cleaner/Memex.out.html"), str4);
    }

    @Test @Disabled
    public void readZim() throws IOException {
        var zr = new ZIMReader(new ZIMFile("/home/vlofgren/Work/wikipedia_en_all_nopic_2021-01.zim"));
//        try (var pw = new PrintWriter(new File("/home/vlofgren/Work/article-clusters.tsv"))) {
//            zr.enumerateArticles(pw);
//        }
        zr.forEachArticles((url, art) -> {
            if (art != null) {
                System.out.println(url);
            }
//            if (art != null && art.length() > 5) {
//                System.out.println(url + " -> " + art.substring(0, 5));
//            }
        }, (p) -> true);

        /*try (var baos = zr.getArticleData("Giraffe", 'A')) {
            String str  = baos.toString();
            Files.writeString(Path.of("/home/vlofgren/Work/wiki-cleaner/Giraffe.wiki.html"), str);
            Files.writeString(Path.of("/home/vlofgren/Work/wiki-cleaner/Giraffe.out.html"), new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/Giraffe", str));
        }*/
    }
}