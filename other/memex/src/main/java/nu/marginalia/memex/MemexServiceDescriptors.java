package nu.marginalia.memex;

import nu.marginalia.memex.auth.AuthMain;
import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.List;

public class MemexServiceDescriptors {
    public static ServiceDescriptors descriptors = new ServiceDescriptors(
            List.of(
                    new ServiceDescriptor(ServiceId.Other_Memex, 5030),
                    new ServiceDescriptor (ServiceId.Other_Auth, 5003)));
}
