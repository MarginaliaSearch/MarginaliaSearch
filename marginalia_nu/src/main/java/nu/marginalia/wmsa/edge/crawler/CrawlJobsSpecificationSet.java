package nu.marginalia.wmsa.edge.crawler;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawler.worker.data.CrawlJobsSpecification;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CrawlJobsSpecificationSet {
    private final List<CrawlJobsSpecification> specs = new ArrayList<>();

    @SneakyThrows
    @Inject
    public CrawlJobsSpecificationSet(@Named("crawl-specifications-path") Path specsFile) {
        Files.readAllLines(specsFile)
                .stream()
                .map(this::stripComments)
                .filter(StringUtils::isNotBlank)
                .flatMap(this::generateSpecsFromLine)
                .map(CrawlJobsSpecification::new)
                .forEach(specs::add);
    }

    private Stream<Integer> generateSpecsFromLine(String s) {
        String[] parts = s.split("\\s");
        if (parts.length == 1) {
            return Stream.of(Integer.parseInt(s));
        }
        else {
            int times = Integer.parseInt(parts[0]);
            int config = Integer.parseInt(parts[1]);
            return Stream.generate(() -> config).limit(times);
        }
    }

    private String stripComments(String s) {
        return s.replaceAll("#.*", "");
    }

    CrawlJobsSpecification get(int i) {
        return specs.get(i);
    }
    int size() {
        return specs.size();
    }
}
