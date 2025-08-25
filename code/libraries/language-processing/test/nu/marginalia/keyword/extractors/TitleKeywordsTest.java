package nu.marginalia.keyword.extractors;

import com.google.common.collect.Sets;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.util.TestLanguageModels;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

class TitleKeywordsTest {

    String document = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>MEMEX - Creepy Website Similarity [ 2022-12-26 ]</title>
                <link rel="stylesheet" href="/style-new.css" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
               \s
            </head>
            <body class="double" lang="en">
                        
            <header>
                <nav>
                    <a href="http://www.marginalia.nu/">Marginalia</a>
                    <a href="http://search.marginalia.nu/">Search Engine</a>
                    <a href="http://encyclopedia.marginalia.nu/">Encyclopedia</a>
                </nav>
            </header>
            <nav class="topbar">
              <h1>Memex</h1>
                        
                <a href="/" class="path root"><img src="/ico/root.png" title="root"> marginalia</a>
                        
                <a href="/log" class="path dir"><img src="/ico/dir.png" title="dir"> log</a>
                        
                <a href="/log/69-creepy-website-similarity.gmi" class="path file"><img src="/ico/file.png" title="file"> 69-creepy-website-similarity.gmi</a>
                        
            </nav>
                        
            <article>
            <section id="memex-node">
            <h1 id="1">Creepy Website Similarity [ 2022-12-26 ]</h1>
            <br>
            This is a write-up about an experiment from a few months ago, in how to find websites that are similar to each other. Website similarity is useful for many things, including discovering new websites to crawl, as well as suggesting similar websites in the Marginalia Search random exploration mode.<br>
            <br>
            <dl class="link"><dt><a class="external" href="https://explore2.marginalia.nu/">https://explore2.marginalia.nu/</a></dt><dd>A link to a slapdash interface for exploring the experimental data.</dd></dl>
            <br>
            The approach chosen was to use the link graph look for websites that are linked to from the same websites. This turned out to work remarkably well. <br>
            <br>
            There are some alternative feature spaces that might have been used, such as TF-IDF data. Using incident links turned out to be surprisingly powerful, almost to an uncanny degree as it's able to find similarities even among websites that Marginalia doesn't index.<br>
            <br>
            As a whole the feature shares a lot of similarity with how you would construct a recommendation algorithm of the type "other shoppers also bought", and in doing so also exposes how creepy they can be. You can't build a recommendation engine without building a tool for profiling. It's largely the same thing.<br>
            <br>
            If you for example point the website explorer to the fringes of politics, it will map that web-space with terrifying accuracy.<br>
            <br>
            <dl class="link"><dt><a class="external" href="https://explore2.marginalia.nu/search?domain=qanon.pub">https://explore2.marginalia.nu/search?domain=qanon.pub</a></dt><dd>qanon.pub's neighbors</dd></dl>
            <br>
            Note again how few of those websites are actually indexed by Marginalia. Only those websites with 'MS' links are! The rest are inferred from the data. On the one hand it's fascinating and cool, on the other it's deeply troubling: If I can create such a map on PC in my living room, imagine what might be accomplished with a datacenter.<br>
            <br>
            You might think "Well what's the problem? QAnon deserves all the scrutiny, give them nowhere to hide!". Except this sort of tool could concievably work just as well as well for mapping democracy advocates in Hong Kong, Putin-critics in Russia, gay people in Uganda, and so forth.<br>
            <br>
            <h2 id="1.1">Implementation details</h2>
            <br>
            In practice, cosine similarity is used to compare the similarity between websites. This is a statistical method perhaps most commonly used in machine learning, but it has other uses as well. <br>
            <br>
            Cosine similarity is calculated by taking the inner product of two vectors and dividing by their norms<br>
            <br>
            <pre>
                   a x b
              p = ---------\s
                  |a| |b|</pre>
            <br>
            As you might remember from linear algebra, this is a measure of how much two vectors "pull in the same direction". The cosine similarity of two identical vectors is unity, and for orthogonal vectors it is zero.<br>
            <br>
            This data has extremely high dimensionality, the vector space consists of nearly 10 million domains, so most "standard" tools like numpy/scipy will not load the data without serious massaging. That juice doesn't appear to be worth the squeeze when it's just as easy to roll what you need on your own (which you'd probably need to do regardless to get it into those tools, Random Reprojection or some such). <br>
            <br>
            Since the vectors in questions are just bitmaps, either a website has a link or it does not, the vector product can be simplified to a logical AND operation. The first stab at the problem was to use RoaringBitmaps.<br>
            <br>
            <pre>
                double cosineSimilarity(RoaringBitmap a, RoaringBitmap b) {
                    double andCardinality = RoaringBitmap.andCardinality(a, b);
                    andCardinality /= Math.sqrt(a.getCardinality());
                    andCardinality /= Math.sqrt(b.getCardinality());
                    return andCardinality;
                }
            </pre>
            <br>
            This works but it's just a bit too slow to be practical. Sacrificing some memory for speed turns out to be necessary. Roaring Bitmaps are memory efficient, but a general purpose library. It's easy to create a drop-in replacement that implements only andCardinality() and getCardinality() in a way that caters to the specifics of the data. <br>
            <br>
            A simple 64 bit bloom filter makes it possible to short-circuit a lot of the calculations since many vectors are small and trivially don't overlap. The vector data is stored in sorted lists. Comparing sorted lists is very cache friendly and fast, while using relatively little memory. Storing a dense matrix would require RAM on the order of hundreds of terabytes so that's no good.<br>
            <br>
            The actual code rewritten for brevity, as a sketch the and-cardinality calculation looks like this, and performs about 5-20x faster than RoaringBitmaps for this specfic use case:<br>
            <br>
            <pre>
                        
                int andCardinality(AndCardIntSet a, AndCardIntSet b) {
                        
                    if ((a.hash & b.hash) == 0) {
                        return 0;
                    }
                        
                    int i = 0, j = 0;
                    int card = 0;
                        
                    do {
                        int diff = a.backingList.getQuick(i) - b.backingList.getQuick(j);
                        
                        if (diff &lt; 0) i++;
                        else if (diff &gt; 0) j++;
                        else {
                            i++;
                            j++;
                            card++;
                        }
                    } while (i &lt; a.getCardinality() && j &lt; b.getCardinality());
                        
                    return card;
                   \s
                 }
            </pre>
            <br>
            This calculates similarities between websites at a rate where it's feasible to pre-calculate the similarities between all known websites within a couple of days. It's on the cusp of being viable to offer ad-hoc calculations, but not quite without being a denial of service-hazard. <br>
            <br>
            To do this in real time, the search space could be reduced using some form of locality-sensitive hash scheme, although for a proof of concept this performs well enough on its own. <br>
            <br>
            <h2 id="1.2">Closing thoughts</h2>
            <br>
            This has been online for a while and I've been debating whether to do this write-up. To be honest this is probably the creepiest piece of software I've built.  <br>
            <br>
            At the same time, I can't imagine I'm the first to conceive of doing this. To repeat, you almost can't build a suggestions engine without this type of side-effect, and recommendations are *everywhere* these days. They are on Spotify, Youtube, Facebook, Reddit, Twitter, Amazon, Netflix, Google, even small web shops have them. <br>
            <br>
            In that light, it's better to make the discovery public and highlight its potential so that it might serve as an example of how and why these recommendation algorithms are profoundly creepy. <br>
            <br>
            <h2 id="1.3">Topic</h2>
            <br>
            <a class="internal" href="/topic/astrolabe.gmi">/topic/astrolabe.gmi</a><br>
            <a class="internal" href="/topic/programming.gmi">/topic/programming.gmi</a><br>
                        
                        
                        
            </section>
            <div id="sidebar">
            <section class="tools">
                <h1>69-creepy-website-similarity.gmi</h1>
                <a class="download" href="/api/raw?url=/log/69-creepy-website-similarity.gmi">Raw</a><br>
                <a rel="nofollow" href="/api/update?url=/log/69-creepy-website-similarity.gmi" class="verb">Edit</a>
                <a rel="nofollow" href="/api/rename?type=gmi&url=/log/69-creepy-website-similarity.gmi" class="verb">Rename</a>
               \s
                <br/>
                <div class="toc">
               \s
                    <a href="#1" class="heading-1">1 Creepy Website Similarity [ 2022-12-26 ]</a>
               \s
                    <a href="#1.1" class="heading-2">1.1 Implementation details</a>
               \s
                    <a href="#1.2" class="heading-2">1.2 Closing thoughts</a>
               \s
                    <a href="#1.3" class="heading-2">1.3 Topic</a>
               \s
                </div>
            </section>
                        
                        
            <section id="memex-backlinks">
            <h1 id="backlinks"> Backlinks </h1>
            <dl>
            <dt><a href="/log/73-new-approach-to-ranking.gmi">/log/73-new-approach-to-ranking.gmi</a></dt>
            <dd>A new approach to domain ranking [ 2023-02-06 ] - See Also</dd>
            </dl>
            </section>
                        
                        
            </div>
            </article>
            <footer>
              Reach me at <a class="fancy-teknisk" href="mailto:kontakt@marginalia.nu">kontakt@marginalia.nu</a>.
              <br />
            </footer>
            </body>
            """;

    @Test
    public void extractTitleWords() throws IOException, ParserConfigurationException, SAXException, UnsupportedLanguageException {
        var se = new SentenceExtractor(new LanguageConfiguration(TestLanguageModels.getLanguageModels()), TestLanguageModels.getLanguageModels());

        var dld = se.extractSentences(Jsoup.parse(document));

        var reps = new TitleKeywords(new KeywordExtractor(), dld).getReps();
        var words = reps.stream().map(rep -> rep.word).collect(Collectors.toSet());

        Set<String> expected = Set.of(
                "creepy",
                "website",
                "similarity",
                "creepy_website",
                "website_similarity",
                "creepy_website_similarity",
                "memex", "2022-12-26");

        Assertions.assertEquals(Collections.emptySet(),
                Sets.symmetricDifference(words, expected));
    }
}