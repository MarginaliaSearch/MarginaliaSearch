package nu.marginalia.wmsa.edge.index.service;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

class SearchIndexConverterTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Test @Disabled
    public void test() {
      //  File dictFile = new File("/home/vlofgren/dictionary.dat");
        File inFile = new File("/home/vlofgren/Work/converter/3/page-index.dat");

        new SearchIndexConverter(IndexBlock.Title, 0, Path.of("/tmp"), inFile,
                new File("/home/vlofgren/Work/converter/words.dat"),
                new File("/home/vlofgren/Work/converter/urls.dat"), new SearchIndexPartitioner(null), val -> false);

      //  sanityCheck();
    }

    @Test @Disabled
    public void sanityCheck() {
        File inFile = new File("/home/vlofgren/write/6/page-index.dat");

//        SearchIndexReader sir = new SearchIndexReader(new SearchIndex[]{
//                new SearchIndex("body", Path.of("/tmp"),
//                        new File("/home/vlofgren/data/urls.dat"),
//                        new File("/home/vlofgren/data/words.dat")),
//                new SearchIndex("body", Path.of("/tmp"),
//                        new File("/home/vlofgren/data/urls.dat"),
//                        new File("/home/vlofgren/data/words.dat"))
//                ,
//                new SearchIndex("body", Path.of("/tmp"),
//                        new File("/home/vlofgren/data/urls.dat"),
//                        new File("/home/vlofgren/data/words.dat"))
//                ,
//                new SearchIndex("body", Path.of("/tmp"),
//                        new File("/home/vlofgren/data/urls.dat"),
//                        new File("/home/vlofgren/data/words.dat"))
//                });

//        getQuery(sir, new EdgeIndexSearchTerms(List.of(152, 106), Collections.emptyList())).stream().forEach(System.out::println);
//        sir.findWord(152).also(106).stream().forEach(System.out::println);
//        scanFile(inFile, (url, word) -> {
//            //System.out.println(url + " " + word);
//            if (!sir.findWord(word).stream().anyMatch(url::equals)) {
//                logger.error("Can't find word {} in {}", word, url);
//            }
//        });


    }
/*
    private SearchIndexReader.Query getQuery(SearchIndexReader indexReader, EdgeIndexSearchTerms searchTerms) {
        var orderedIncludes = searchTerms.includes
                .stream()
                .sorted(Comparator.comparingLong(indexReader::numHits))
                .distinct()
                .mapToInt(Integer::intValue)
                .toArray();

        logger.info("Includes: ({}); excludes: ({})", Arrays.
                        stream(orderedIncludes)
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(",")),
                searchTerms.excludes.stream().map(String::valueOf).collect(Collectors.joining(",")));
        SearchIndexReader.Query query = indexReader.findWord(orderedIncludes[0]);
        for (int i = 1; i < orderedIncludes.length; i++) {
            query = query.also(orderedIncludes[i]);
        }
        for (int term : searchTerms.excludes) {
            query = query.not(term);
        }
        return query;
    }

*/
}