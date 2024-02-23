package nu.marginalia.process.control;

/** Dummy implementation of ProcessHeartbeat that does nothing */
public class FakeProcessHeartbeat implements ProcessHeartbeat {

    @Override
    public <T extends Enum<T>> ProcessTaskHeartbeat<T> createProcessTaskHeartbeat(Class<T> steps, String processName) {
        return new ProcessTaskHeartbeat<>() {
            @Override
            public void progress(T step) {}

            @Override
            public void shutDown() {}

            @Override
            public void close() {}
        };
    }

    @Override
    public ProcessAdHocTaskHeartbeat createAdHocTaskHeartbeat(String processName) {
        return new ProcessAdHocTaskHeartbeat() {
            @Override
            public void progress(String step, int progress, int total) {}

            @Override
            public void close() {}
        };
    }

    @Override
    public void setProgress(double progress) {}


}
