package nu.marginalia;


import nu.marginalia.service.ServiceHomeNotConfiguredException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class WmsaHome {
    public static UserAgent getUserAgent()  {

        return new UserAgent(
                System.getProperty("crawler.userAgentString", "Mozilla/5.0 (compatible; Marginalia-like bot; +https://git.marginalia.nu/))"),
                System.getProperty("crawler.userAgentIdentifier", "search.marginalia.nu")
        );
    }


    public static Path getUploadDir() {
        return Path.of(
                System.getProperty("executor.uploadDir", "/uploads")
        );
    }

    public static Path getHomePath() {
        String[] possibleLocations = new String[] {
            System.getenv("WMSA_HOME"),
            System.getProperty("system.homePath"),
            "/var/lib/wmsa",
            "/wmsa"
        };

        Optional<String> retStr = Stream.of(possibleLocations)
                .filter(Objects::nonNull)
                .map(Path::of)
                .filter(Files::isDirectory)
                .map(Path::toString)
                .findFirst();

        if (retStr.isEmpty()) {
            // Check if we are running in a test environment

            var testRoot = Stream.iterate(Paths.get("").toAbsolutePath(), f -> f != null && Files.exists(f), Path::getParent)
                    .filter(p -> Files.exists(p.resolve("run/env")))
                    .filter(p -> Files.exists(p.resolve("run/setup.sh")))
                    .map(p -> p.resolve("run"))
                    .findAny();

            return testRoot.orElseThrow(() -> new ServiceHomeNotConfiguredException("""
                            Could not find $WMSA_HOME, either set environment
                            variable, the 'system.homePath' property,
                            or ensure either /wmssa or /var/lib/wmsa exists
                            """));
        }

        var ret = Path.of(retStr.get());

        if (!Files.isDirectory(ret.resolve("model"))) {
            throw new ServiceHomeNotConfiguredException("You need to run 'run/setup.sh' to download models to run/ before this will work!");
        }

        return ret;
    }

    public static Path getAdsDefinition() {
        return getHomePath().resolve("data").resolve("adblock.txt");
    }

    public static Path getIPLocationDatabse() {
        return getHomePath().resolve("data").resolve("IP2LOCATION-LITE-DB1.CSV");

    }

    public static Path getAsnMappingDatabase() {
        return getHomePath().resolve("data").resolve("asn-data-raw-table");
    }

    public static Path getAsnInfoDatabase() {
        return getHomePath().resolve("data").resolve("asn-used-autnums");
    }

    public static LanguageModels getLanguageModels() {
        final Path home = getHomePath();

        return new LanguageModels(
                home.resolve("model/ngrams.bin"),
                home.resolve("model/tfreq-new-algo3.bin"),
                home.resolve("model/opennlp-sentence.bin"),
                home.resolve("model/English.RDR"),
                home.resolve("model/English.DICT"),
                home.resolve("model/opennlp-tok.bin"),
                home.resolve("model/lid.176.ftz"));
    }

    public static Path getAtagsPath() {
        return getHomePath().resolve("data/atags.parquet");
    }


}
