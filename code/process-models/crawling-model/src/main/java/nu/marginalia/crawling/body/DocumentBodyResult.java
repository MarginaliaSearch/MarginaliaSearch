package nu.marginalia.crawling.body;

import nu.marginalia.crawling.model.CrawlerDocumentStatus;

import java.util.Optional;
import java.util.function.BiFunction;

public sealed interface DocumentBodyResult<T> {
    record Ok<T>(String contentType, T body) implements DocumentBodyResult<T> {

        @Override
        public <T2> Optional<T2> mapOpt(BiFunction<String, T, T2> mapper) {
            return Optional.of(mapper.apply(contentType, body));
        }

        @Override
        public void ifPresent(ExConsumer<T, Exception> consumer) throws Exception {
            consumer.accept(contentType, body);
        }
    }
    record Error<T>(CrawlerDocumentStatus status, String why) implements DocumentBodyResult<T> {
        @Override
        public <T2> Optional<T2> mapOpt(BiFunction<String, T, T2> mapper) {
            return Optional.empty();
        }

        @Override
        public void ifPresent(ExConsumer<T, Exception> consumer) throws Exception {
        }
    }

    <T2> Optional<T2> mapOpt(BiFunction<String, T, T2> mapper);

    void ifPresent(ExConsumer<T,Exception> consumer) throws Exception;

    interface ExConsumer<T,E extends Exception> {
        void accept(String contentType, T t) throws E;
    }
}
