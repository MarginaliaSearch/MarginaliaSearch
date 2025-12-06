package nu.marginalia.service;

import com.google.inject.ImplementedBy;

import java.util.List;

@ImplementedBy(NodeConfigurationWatcher.class)
public interface NodeConfigurationWatcherIf {
    List<Integer> getQueryNodes();
}
