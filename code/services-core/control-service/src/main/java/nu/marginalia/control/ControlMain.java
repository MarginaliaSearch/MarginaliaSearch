package nu.marginalia.control;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.service.server.Initialization;

public class ControlMain extends MainClass {

    @Inject
    public ControlMain(ControlService service) {
    }

    public static void main(String... args) {
        init(ServiceId.Control, args);

        Injector injector = Guice.createInjector(
                new DatabaseModule(true),
                new ControlProcessModule(),
                new ServiceConfigurationModule(ServiceId.Control));

        injector.getInstance(ControlMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
