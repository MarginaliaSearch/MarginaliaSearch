package nu.marginalia.service;

import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.List;

public class SearchServiceDescriptors {
    public static ServiceDescriptors descriptors = new ServiceDescriptors(
                  List.of(new ServiceDescriptor(ServiceId.Api),
                          new ServiceDescriptor(ServiceId.Index),
                          new ServiceDescriptor(ServiceId.Query),
                          new ServiceDescriptor(ServiceId.Search),
                          new ServiceDescriptor(ServiceId.Executor),
                          new ServiceDescriptor(ServiceId.Assistant),
                          new ServiceDescriptor(ServiceId.Dating),
                          new ServiceDescriptor(ServiceId.Explorer),
                          new ServiceDescriptor(ServiceId.Control)
                  ));
}
