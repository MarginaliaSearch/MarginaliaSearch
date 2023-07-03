package nu.marginalia.service;

import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.List;

public class SearchServiceDescriptors {
    public static ServiceDescriptors descriptors = new ServiceDescriptors(
                  List.of(new ServiceDescriptor(ServiceId.Api, 5004),
                          new ServiceDescriptor(ServiceId.Index, 5021),
                          new ServiceDescriptor(ServiceId.Search, 5023),
                          new ServiceDescriptor(ServiceId.Assistant, 5025),
                          new ServiceDescriptor(ServiceId.Dating, 5070),
                          new ServiceDescriptor(ServiceId.Explorer, 5071),
                          new ServiceDescriptor(ServiceId.Control, 5090)
                  ));
}
