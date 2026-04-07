package nu.marginalia.livecapture;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class LivecaptureModule extends AbstractModule {
    public void configure() {
        bind(String.class)
                .annotatedWith(Names.named("headless-uri"))
                .toInstance(System.getProperty("live-capture.headless-uri", ""));
        bind(Integer.class)
                .annotatedWith(Names.named("headless-agent-threads"))
                .toInstance(Integer.parseInt(System.getProperty("live-capture.headless-agent-threads", "4")));
        bind(Integer.class)
                .annotatedWith(Names.named("headless-sample-threads"))
                .toInstance(Integer.parseInt(System.getProperty("live-capture.headless-sample-threads", "4")));
    }
}
