package nu.marginalia.wmsa.renderer;

import nu.marginalia.service.descriptor.ServiceDescriptor;
import nu.marginalia.service.descriptor.ServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.List;

public class WmsaServiceDescriptors {
    public static ServiceDescriptors descriptors = new ServiceDescriptors(
                  List.of(
                          new ServiceDescriptor(ServiceId.Other_ResourceStore, 5000),
                          new ServiceDescriptor(ServiceId.Other_Renderer, 5002),
                          new ServiceDescriptor(ServiceId.Other_PodcastScraper, 5013)));


}
