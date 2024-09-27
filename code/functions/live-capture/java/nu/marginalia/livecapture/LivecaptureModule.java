package nu.marginalia.livecapture;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class LivecaptureModule extends AbstractModule {
    public void configure() {
        bind(String.class)
                .annotatedWith(Names.named("browserless-uri"))
                .toInstance(System.getProperty("live-capture.browserless-uri", ""));
        bind(Integer.class)
                .annotatedWith(Names.named("browserless-agent-threads"))
                .toInstance(Integer.parseInt(System.getProperty("live-capture.browserless-agent-threads", "4")));
    }
}
