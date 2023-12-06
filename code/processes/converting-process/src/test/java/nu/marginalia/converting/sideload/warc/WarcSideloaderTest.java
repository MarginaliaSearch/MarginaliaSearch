package nu.marginalia.converting.sideload.warc;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.converting.processor.ConverterDomainTypes;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.netpreserve.jwarc.WarcWriter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.when;

class WarcSideloaderTest extends AbstractModule {
    SideloaderProcessing processing;

    Path warcFile;
    @BeforeEach
    public void setUp() throws IOException {
        processing = Guice.createInjector(new ConverterModule(), this)
                .getInstance(SideloaderProcessing.class);
        warcFile = Files.createTempFile(getClass().getSimpleName(), ".warc.gz");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(warcFile);
    }

    public void configure() {
        var domainTypesMock = Mockito.mock(ConverterDomainTypes.class);
        when(domainTypesMock.isBlog(Mockito.any())).thenReturn(false);

        bind(ConverterDomainTypes.class).toInstance(domainTypesMock);
    }


    @Test
    public void test() throws IOException {
        try (var writer = new WarcWriter(Files.newOutputStream(warcFile))) {
            writer.fetch(new URI("https://www.marginalia.nu/"));
            writer.fetch(new URI("https://www.marginalia.nu/log/93_atags/"));
            writer.fetch(new URI("https://www.marginalia.nu/links/"));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try (var sideloader = new WarcSideloader(warcFile, processing)) {

            var domain = sideloader.getDomain();
            System.out.println(domain);
            sideloader.getDocumentsStream().forEachRemaining(System.out::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}