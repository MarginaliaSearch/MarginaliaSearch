package nu.marginalia.service.control;

/** Dummy implementation of ServiceHeartbeat that does nothing */
public class FakeServiceHeartbeat implements ServiceHeartbeat {
    @Override
    public <T extends Enum<T>> ServiceTaskHeartbeat<T> createServiceTaskHeartbeat(Class<T> steps, String processName) {
        return new ServiceTaskHeartbeat<T>() {
            @Override
            public void progress(T step) {}
            @Override
            public void close() {}
        };
    }
}
