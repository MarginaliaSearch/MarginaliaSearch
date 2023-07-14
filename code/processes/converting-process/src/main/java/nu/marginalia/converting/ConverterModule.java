package nu.marginalia.converting;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import lombok.SneakyThrows;
import nu.marginalia.LanguageModels;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.mqapi.ConvertRequest;
import nu.marginalia.db.storage.FileStorageService;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.persistence.MqPersistence;
import plan.CrawlPlan;
import nu.marginalia.model.gson.GsonFactory;
import plan.CrawlPlanLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ConverterModule extends AbstractModule {

    public ConverterModule() {
    }

    public void configure() {
        bind(Gson.class).toInstance(createGson());

        bind(ProcessConfiguration.class).toInstance(new ProcessConfiguration("converter", 0, UUID.randomUUID()));

        bind(Double.class).annotatedWith(Names.named("min-document-quality")).toInstance(-15.);
        bind(Integer.class).annotatedWith(Names.named("min-document-length")).toInstance(250);
        bind(Integer.class).annotatedWith(Names.named("max-title-length")).toInstance(128);
        bind(Integer.class).annotatedWith(Names.named("max-summary-length")).toInstance(255);

        bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
    }

    private Gson createGson() {
        return GsonFactory.get();
    }

}
