package nu.marginalia.service.control;

import com.google.inject.ImplementedBy;

@ImplementedBy(ServiceHeartbeatImpl.class)
public interface ServiceHeartbeat {
    <T extends Enum<T>> ServiceTaskHeartbeat<T> createServiceTaskHeartbeat(Class<T> steps, String processName);
}
