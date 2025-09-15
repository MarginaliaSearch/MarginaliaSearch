package nu.marginalia;


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
            // Check parent directories for a fingerprint of the project's installation boilerplate
            var prodRoot = Stream.iterate(Paths.get("").toAbsolutePath(), f -> f != null && Files.exists(f), Path::getParent)
                    .filter(p -> Files.exists(p.resolve("conf/properties/system.properties")))
                    .filter(p -> Files.exists(p.resolve("model/tfreq-new-algo3.bin")))
                    .findAny();
            if (prodRoot.isPresent()) {
                return prodRoot.get();
            }

            // Check if we are running in a test environment by looking for fingerprints
            // matching the base of the source tree for the project, then looking up the
            // run directory which contains a template for the installation we can use as
            // though it's the project root for testing purposes

            var testRoot = Stream.iterate(Paths.get("").toAbsolutePath(), f -> f != null && Files.exists(f), Path::getParent)
                    .filter(p -> Files.exists(p.resolve("run/env")))
                    .filter(p -> Files.exists(p.resolve("run/setup.sh")))
                    .map(p -> p.resolve("run"))
                    .findAny();

            return testRoot.orElseThrow(() -> new IllegalStateException("""
                            Could not find $WMSA_HOME, either set environment
                            variable, the 'system.homePath' java property,
                            or ensure either /wmsa or /var/lib/wmsa exists
                            """));
        }

        var ret = Path.of(retStr.get());

        if (!Files.isDirectory(ret.resolve("model"))) {
            throw new IllegalStateException("You need to run 'run/setup.sh' to download models to run/ before this will work!");
        }

        return ret;
    }

    public static Path getDataPath() {
        return getHomePath().resolve("data");
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
                home.resolve("model/tfreq-new-algo3.bin"),
                home.resolve("model/opennlp-sentence.bin"),
                home.resolve("model/English.RDR"),
                home.resolve("model/English.DICT"),
                home.resolve("model/lid.176.ftz"),
                home.resolve("model/segments.bin")
                );
    }

    public static Path getAtagsPath() {
        return getHomePath().resolve("data/atags.parquet");
    }


    public static Path getLangugeConfig() {
        return getHomePath().resolve("conf/languages.xml");
    }
}
