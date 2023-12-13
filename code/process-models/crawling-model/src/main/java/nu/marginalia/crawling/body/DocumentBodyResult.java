package nu.marginalia.crawling.body;

import nu.marginalia.crawling.model.CrawlerDocumentStatus;

import java.util.Optional;
import java.util.function.BiFunction;

public sealed interface DocumentBodyResult {
    record Ok(String contentType, String body) implements DocumentBodyResult {
        @Override
        public <T> Optional<T> map(BiFunction<String, String, T> fun) {
            return Optional.of(fun.apply(contentType, body));
        }
    }
    record Error(CrawlerDocumentStatus status, String why) implements DocumentBodyResult {
        @Override
        public <T> Optional<T> map(BiFunction<String, String, T> fun) {
            return Optional.empty();
        }
    }

    <T> Optional<T> map(BiFunction<String, String, T> fun);
}
