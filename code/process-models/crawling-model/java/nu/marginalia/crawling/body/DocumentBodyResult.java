package nu.marginalia.crawling.body;

import nu.marginalia.contenttype.ContentType;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;

import java.util.Optional;
import java.util.function.BiFunction;

public sealed interface DocumentBodyResult<T> {
    record Ok<T>(ContentType contentType, T body) implements DocumentBodyResult<T> {

        @Override
        public <T2> Optional<T2> mapOpt(BiFunction<ContentType, T, T2> mapper) {
            return Optional.of(mapper.apply(contentType, body));
        }
        @Override
        public <T2> Optional<T2> flatMapOpt(BiFunction<ContentType, T, Optional<T2>> mapper) {
            return mapper.apply(contentType, body);
        }

        @Override
        public <T2> DocumentBodyResult<T2> flatMap(BiFunction<ContentType, T, DocumentBodyResult<T2>> mapper) {
            return mapper.apply(contentType, body);
        }

        @Override
        public void ifPresent(ExConsumer<T, Exception> consumer) throws Exception {
            consumer.accept(contentType, body);
        }
    }
    record Error<T>(CrawlerDocumentStatus status, String why) implements DocumentBodyResult<T> {
        @Override
        public <T2> Optional<T2> mapOpt(BiFunction<ContentType, T, T2> mapper) {
            return Optional.empty();
        }
        public <T2> Optional<T2> flatMapOpt(BiFunction<ContentType, T, Optional<T2>> mapper) { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T2> DocumentBodyResult<T2> flatMap(BiFunction<ContentType, T, DocumentBodyResult<T2>> mapper) {
            return (DocumentBodyResult<T2>) this;
        }

        @Override
        public void ifPresent(ExConsumer<T, Exception> consumer) throws Exception {
        }
    }

    <T2> Optional<T2> mapOpt(BiFunction<ContentType, T, T2> mapper);
    <T2> Optional<T2> flatMapOpt(BiFunction<ContentType, T, Optional<T2>> mapper);
    <T2> DocumentBodyResult<T2> flatMap(BiFunction<ContentType, T, DocumentBodyResult<T2>> mapper);

    void ifPresent(ExConsumer<T,Exception> consumer) throws Exception;

    interface ExConsumer<T,E extends Exception> {
        void accept(ContentType contentType, T t) throws E;
    }
}
