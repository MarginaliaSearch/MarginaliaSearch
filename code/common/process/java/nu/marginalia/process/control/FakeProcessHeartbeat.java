package nu.marginalia.process.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dummy implementation of ProcessHeartbeat that does nothing */
public class FakeProcessHeartbeat implements ProcessHeartbeat {
    private static final Logger logger = LoggerFactory.getLogger(FakeProcessHeartbeat.class);
    @Override
    public <T extends Enum<T>> ProcessTaskHeartbeat<T> createProcessTaskHeartbeat(Class<T> steps, String processName) {
        return new ProcessTaskHeartbeat<>() {
            @Override
            public void progress(T step) {
                logger.info("Progress: {}", step);
            }

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
            public void progress(String step, int progress, int total) {
                logger.info("Progress: {}, {}/{}", step, progress, total);
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public void setProgress(double progress) {}


}
