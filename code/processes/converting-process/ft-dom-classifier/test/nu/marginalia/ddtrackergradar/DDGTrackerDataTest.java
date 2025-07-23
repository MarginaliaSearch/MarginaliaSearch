package nu.marginalia.ddtrackergradar;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class DDGTrackerDataTest {
    @Test
    public void testLoad() {
        DDGTrackerData data = new DDGTrackerData();
        data.loadDomainDir(Path.of("/home/vlofgren/Work/tracker-radar/domains/US/"));
        data.getDomainInfo("hotjar.com").ifPresent(System.out::println);
        data.getAllClassifications().forEach(System.out::println);
    }

}