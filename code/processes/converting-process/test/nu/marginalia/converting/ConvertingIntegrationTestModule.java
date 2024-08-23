package nu.marginalia.converting;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import nu.marginalia.LanguageModels;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.ConverterDomainTypes;
import nu.marginalia.service.module.ServiceConfiguration;
import org.mockito.Mockito;

public class ConvertingIntegrationTestModule  extends AbstractModule {
    public void configure() {
        bind(Double.class).annotatedWith(Names.named("min-document-quality")).toInstance(-15.);
        bind(Integer.class).annotatedWith(Names.named("min-document-length")).toInstance(250);
        bind(Integer.class).annotatedWith(Names.named("max-title-length")).toInstance(128);
        bind(Integer.class).annotatedWith(Names.named("max-summary-length")).toInstance(255);
        bind(ServiceConfiguration.class).toInstance(new ServiceConfiguration(new String[0], null, 1, "localhost", "localhost", 0, null));
        bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration(
                "converting-process", 1, null
        ));
        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
        bind(ConverterDomainTypes.class).toInstance(Mockito.mock(ConverterDomainTypes.class));
    }
}
