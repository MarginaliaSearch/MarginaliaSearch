package nu.marginalia;


import nu.marginalia.service.ServiceHomeNotConfiguredException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        var retStr = Optional.ofNullable(System.getenv("WMSA_HOME")).orElseGet(WmsaHome::findDefaultHomePath);

        var ret = Path.of(retStr);

        if (!Files.isDirectory(ret)) {
            throw new ServiceHomeNotConfiguredException("Could not find $WMSA_HOME, either set environment variable or ensure " + retStr + " exists");
        }


        if (!Files.isDirectory(ret.resolve("model"))) {
            throw new ServiceHomeNotConfiguredException("You need to run 'run/setup.sh' to download models to run/ before this will work!");
        }

        return ret;
    }

    private static String findDefaultHomePath() {

        // Assume this is a local developer and not a production system, since it would have WMSA_HOME set.
        // Developers probably have a "run/" somewhere upstream from cwd.
        //

        return Stream.iterate(Paths.get("").toAbsolutePath(), f -> f != null && Files.exists(f), Path::getParent)
                .filter(p -> Files.exists(p.resolve("run/env")))
                .filter(p -> Files.exists(p.resolve("run/setup.sh")))
                .map(p -> p.resolve("run"))
                .findAny()
                .orElse(Path.of("/var/lib/wmsa"))
                .toString();
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
