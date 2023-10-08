package nu.marginalia.query;

import com.google.inject.Inject;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Service;

public class QueryService extends Service {
    @Inject
    public QueryService(BaseServiceParams params) {
        super(params);
    }
}
