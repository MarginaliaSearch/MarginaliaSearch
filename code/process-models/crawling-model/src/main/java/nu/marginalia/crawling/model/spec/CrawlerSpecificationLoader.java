package nu.marginalia.crawling.model.spec;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.JsonStreamParser;
import lombok.SneakyThrows;
import nu.marginalia.model.gson.GsonFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Iterator;

public class CrawlerSpecificationLoader {
    private final static Gson gson = GsonFactory.get();

    @SneakyThrows
    public static Iterable<CrawlingSpecification> asIterable(Path inputSpec) {
        var inputStream = new BufferedReader(new InputStreamReader(new ZstdInputStream(new FileInputStream(inputSpec.toFile()),
                RecyclingBufferPool.INSTANCE)));
        var parser = new JsonStreamParser(inputStream);

        return () -> new Iterator<>() {
            @Override
            @SneakyThrows
            public boolean hasNext() {
                if (!parser.hasNext()) {
                    inputStream.close();
                    return false;
                }
                return true;
            }

            @Override
            public CrawlingSpecification next() {
                return gson.fromJson(parser.next(), CrawlingSpecification.class);
            }
        };
    }
}
