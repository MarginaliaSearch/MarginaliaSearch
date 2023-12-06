package nu.marginalia.converting.sideload.warc;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.converting.processor.ConverterDomainTypes;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;

import static org.mockito.Mockito.when;

class WarcSideloaderTest {
    @Test
    public void test() throws IOException {
        var domainTypesMock = Mockito.mock(ConverterDomainTypes.class);
        when(domainTypesMock.isBlog(Mockito.any())).thenReturn(false);

        var processing = Guice.createInjector(new ConverterModule(),
                        new AbstractModule() {
                            public void configure() {
                                bind(ConverterDomainTypes.class).toInstance(domainTypesMock);
                            }
                        }
                )
                .getInstance(SideloaderProcessing.class);

        var sideloader = new WarcSideloader(Path.of("/home/vlofgren/marginalia.warc.gz"), processing);

        var domain = sideloader.getDomain();
        System.out.println(domain);
        sideloader.getDocumentsStream().forEachRemaining(System.out::println);
    }
}