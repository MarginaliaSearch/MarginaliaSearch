package nu.marginalia.control;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.WmsaHome;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.module.ServiceDiscoveryModule;
import nu.marginalia.service.server.Initialization;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;

public class ControlMain extends MainClass {

    @Inject
    public ControlMain(ControlService service) {
    }

    public static void main(String... args) throws Exception {
        init(ServiceId.Control, args);

        Injector injector = Guice.createInjector(
                new DatabaseModule(true),
                new ControlProcessModule(),
                new ServiceDiscoveryModule(),
                new ServiceConfigurationModule(ServiceId.Control));

        // Orchestrate the boot order for the services
        var registry = injector.getInstance(ServiceRegistryIf.class);
        var configuration = injector.getInstance(ServiceConfiguration.class);

        // This must be run before orchestrateBoot, so that the other services don't
        // start up until we're done
        downloadAncillaryFiles(WmsaHome.getDataPath());

        orchestrateBoot(registry, configuration);


        injector.getInstance(ControlMain.class);
        injector.getInstance(Initialization.class).setReady();
    }

    static void downloadAncillaryFiles(Path dataPath) throws Exception {
        Path adblockFile = dataPath.resolve("adblock.txt");
        if (!Files.exists(adblockFile)) {
            download(adblockFile, new URI("https://downloads.marginalia.nu/data/adblock.txt"));
        }

        Path suggestionsFile = dataPath.resolve("suggestions.txt");
        if (!Files.exists(suggestionsFile)) {
            downloadGzipped(suggestionsFile, new URI("https://downloads.marginalia.nu/data/suggestions.txt.gz"));
        }

        Path asnRawData = dataPath.resolve("asn-data-raw-table");
        if (!Files.exists(asnRawData)) {
            download(asnRawData, new URI("https://thyme.apnic.net/current/data-raw-table"));
        }

        Path asnUsedAutnums = dataPath.resolve("asn-used-autnums");
        if (!Files.exists(asnUsedAutnums)) {
            download(asnUsedAutnums, new URI("https://thyme.apnic.net/current/data-used-autnums"));
        }

        Path ip2Location = dataPath.resolve("IP2LOCATION-LITE-DB1.CSV");
        Path ip2LocationZip = dataPath.resolve("IP2LOCATION-LITE-DB1.CSV.ZIP");

        if (!Files.exists(ip2Location)) {
            if (Files.exists(ip2LocationZip)) {
                Files.delete(ip2LocationZip);
            }

            download(ip2LocationZip, new URI("https://download.ip2location.com/lite/IP2LOCATION-LITE-DB1.CSV.ZIP"));
            unzip(ip2LocationZip, dataPath, List.of("IP2LOCATION-LITE-DB1.CSV", "README_LITE.TXT", "LICENSE-CC-BY-SA-4.0.TXT"));
            Files.deleteIfExists(ip2LocationZip);
        }
    }

    private static void download(Path dest, URI source) throws IOException {
        System.out.println("Downloading " + source + " to " + dest);
        try {
            if (!Files.exists(dest)) {
                try (var in = new BufferedInputStream(source.toURL().openStream())) {
                    Files.copy(in, dest);
                }
            }
        }
        catch (IOException e) {
            Files.deleteIfExists(dest);
            throw e;
        }
    }

    private static void downloadGzipped(Path dest, URI source) throws IOException {
        System.out.println("Downloading " + source + " to " + dest);
        try {
            if (!Files.exists(dest)) {
                try (var in = new GZIPInputStream(new BufferedInputStream(source.toURL().openStream()))) {
                    Files.copy(in, dest);
                }
            }
        }
        catch (IOException e) {
            Files.deleteIfExists(dest);
            throw e;
        }
    }


    private static void unzip(Path inputZip, Path outputDir, Collection<String> fileNames) throws IOException {
        try (ZipFile zipFile = new ZipFile(inputZip.toFile())) {
            zipFile.stream().forEach(entry -> {
                try {
                    if (fileNames.contains(entry.getName())) {
                        System.out.println("Extracting " + entry.getName());
                        Files.copy(zipFile.getInputStream(entry), outputDir.resolve(entry.getName()));
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
