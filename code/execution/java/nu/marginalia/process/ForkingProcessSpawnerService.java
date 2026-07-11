package nu.marginalia.process;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.service.server.BaseServiceParams;

/** Spawns the batch processes as direct children of the service, sharing its execution
 * environment.  This is the default spawner, and the appropriate one for docker
 * deployments.
 */
@Singleton
public class ForkingProcessSpawnerService extends AbstractProcessSpawnerService {

    @Inject
    public ForkingProcessSpawnerService(BaseServiceParams params) {
        super(params);
    }
}
