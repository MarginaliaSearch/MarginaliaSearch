package nu.marginalia.memex.auth;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import nu.marginalia.memex.MemexServiceDescriptors;
import nu.marginalia.service.MainClass;
import nu.marginalia.service.id.ServiceId;
import nu.marginalia.service.module.ConfigurationModule;
import nu.marginalia.service.server.Initialization;

public class AuthMain extends MainClass {

    @Inject
    public AuthMain(AuthService service) {
    }

    public static void main(String... args) {
        MainClass.init(ServiceId.Other_Auth, args);

        Injector injector = Guice.createInjector(
                new AuthConfigurationModule(),
                new ConfigurationModule(MemexServiceDescriptors.descriptors, ServiceId.Other_Auth));
        injector.getInstance(AuthMain.class);
        injector.getInstance(Initialization.class).setReady();
    }
}
