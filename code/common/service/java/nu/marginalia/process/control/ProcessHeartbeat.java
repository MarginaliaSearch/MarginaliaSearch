package nu.marginalia.process.control;

import com.google.inject.ImplementedBy;

@ImplementedBy(ProcessHeartbeatImpl.class)
public interface ProcessHeartbeat {
    <T extends Enum<T>> ProcessTaskHeartbeat<T> createProcessTaskHeartbeat(Class<T> steps, String processName);
    ProcessAdHocTaskHeartbeat createAdHocTaskHeartbeat(String processName);

    void setProgress(double progress);
}
