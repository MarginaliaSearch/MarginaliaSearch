package nu.marginalia.service.descriptor;

import nu.marginalia.service.SearchServiceDescriptors;
import nu.marginalia.service.id.ServiceId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** @see SearchServiceDescriptors */
public class ServiceDescriptors {
    private final Map<ServiceId, ServiceDescriptor> descriptorsAll = new LinkedHashMap<>();

    public ServiceDescriptors() {

    }

    public ServiceDescriptors(List<ServiceDescriptor> descriptors) {
        descriptors.forEach(d -> descriptorsAll.put(d.id, d));
    }

    public ServiceDescriptor[] values() {
        return descriptorsAll.values().toArray(ServiceDescriptor[]::new);
    }

    public ServiceDescriptor forId(ServiceId id) {
        return Objects.requireNonNull(descriptorsAll.get(id),
                "No service descriptor defined for " + id + " -- did you forget to "
                + "bind(ServiceDescriptors.class).toInstance(SearchServiceDescriptors.descriptors); ?");
    }

}
