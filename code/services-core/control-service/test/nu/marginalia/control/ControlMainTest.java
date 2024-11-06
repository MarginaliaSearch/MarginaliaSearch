package nu.marginalia.control;

import nu.marginalia.test.TestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class ControlMainTest {

    @Test
    @Disabled("We don't want to rudely hammer 3rd party services with chonky downloads on every build")
    void downloadAncillaryFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("test");

        ControlMain.downloadAncillaryFiles(tempDir);

        Assertions.assertTrue(Files.exists(tempDir.resolve("adblock.txt")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("suggestions.txt")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("asn-data-raw-table")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("asn-used-autnums")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("LICENSE-CC-BY-SA-4.0.TXT")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("README_LITE.TXT")));
        Assertions.assertTrue(Files.exists(tempDir.resolve("IP2LOCATION-LITE-DB1.CSV")));

        // We don't want to leave a mess
        Assertions.assertFalse(Files.exists(tempDir.resolve("IP2LOCATION-LITE-DB1.CSV.ZIP")));

        TestUtil.clearTempDir(tempDir);
    }
}