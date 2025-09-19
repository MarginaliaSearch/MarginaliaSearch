package nu.marginalia.index.config;

import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.LanguageDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public sealed interface IndexFileName {
    enum Version {
        CURRENT, NEXT
    }

    record FullWords(String languageIsoCode) implements IndexFileName {}
    record FullDocs() implements IndexFileName {}
    record FullPositions() implements IndexFileName {}

    record PrioWords(String languageIsoCode) implements IndexFileName {}
    record PrioDocs() implements IndexFileName {}

    record ForwardDocIds() implements IndexFileName { }
    record ForwardDocData() implements IndexFileName { }
    record ForwardSpansData() implements IndexFileName { }

    static List<IndexFileName> revFullIndexFiles(LanguageConfiguration languageConfiguration) {
        List<IndexFileName> ret = new ArrayList<>();

        ret.add(new FullDocs());
        ret.add(new FullPositions());

        for (LanguageDefinition ld : languageConfiguration.languages()) {
            ret.add(new FullWords(ld.isoCode()));
        }

        return ret;
    }

    static List<IndexFileName> revPrioIndexFiles(LanguageConfiguration languageConfiguration) {
        List<IndexFileName> ret = new ArrayList<>();

        ret.add(new PrioDocs());

        for (LanguageDefinition ld : languageConfiguration.languages()) {
            ret.add(new PrioWords(ld.isoCode()));
        }

        return ret;
    }

    static List<IndexFileName> forwardIndexFiles() {
        return List.of(
                new ForwardDocData(),
                new ForwardDocIds(),
                new ForwardSpansData()
        );
    }

    static Path resolve(Path basePath, IndexFileName fileName, Version version) {
        return switch (fileName) {
            case FullWords(String isoCode) -> switch (version) {
                case CURRENT -> basePath.resolve("rev-words-%s.dat".formatted(isoCode));
                case NEXT -> basePath.resolve("rev-words-%s.dat.next".formatted(isoCode));
            };
            case FullDocs() -> switch (version) {
                case CURRENT -> basePath.resolve("rev-docs.dat");
                case NEXT -> basePath.resolve("rev-docs.dat.next");
            };
            case FullPositions() -> switch (version) {
                case CURRENT -> basePath.resolve("rev-positions.dat");
                case NEXT -> basePath.resolve("rev-positions.dat.next");
            };
            case PrioWords(String languageIsoCode) -> switch (version) {
                case CURRENT -> basePath.resolve("rev-prio-words-%s.dat".formatted(languageIsoCode));
                case NEXT -> basePath.resolve("rev-prio-words-%s.dat.next".formatted(languageIsoCode));
            };
            case PrioDocs() -> switch (version) {
                case CURRENT -> basePath.resolve("rev-prio-docs.dat");
                case NEXT -> basePath.resolve("rev-prio-docs.dat.next");
            };
            case ForwardDocIds() -> switch (version) {
                case CURRENT -> basePath.resolve("fwd-doc-ids.dat");
                case NEXT -> basePath.resolve("fwd-doc-ids.dat.next");
            };
            case ForwardDocData() -> switch (version) {
                case CURRENT -> basePath.resolve("fwd-doc-data.dat");
                case NEXT -> basePath.resolve("fwd-doc-data.dat.next");
            };
            case ForwardSpansData() -> switch (version) {
                case CURRENT -> basePath.resolve("fwd-spans.dat");
                case NEXT -> basePath.resolve("fwd-spans.dat.next");
            };
        };
    }


}
