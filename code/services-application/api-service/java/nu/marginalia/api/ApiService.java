package nu.marginalia.api;

import com.google.inject.Inject;
import io.jooby.*;
import nu.marginalia.api.svc.LicenseService;
import nu.marginalia.api.svc.ResponseCache;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import nu.marginalia.service.server.mq.MqRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ApiService extends JoobyService {
    private final ResponseCache responseCache;
    private final LicenseService licenseService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ApiV1 apiV1;
    private final ApiV2 apiV2;

    @Inject
    public ApiService(BaseServiceParams params,
                      ResponseCache responseCache,
                      LicenseService licenseService,
                      ApiV1 apiV1,
                      ApiV2 apiV2
                      )
    throws Exception
    {

        super(params,
                List.of(),
                List.of()
        );

        this.responseCache = responseCache;
        this.licenseService = licenseService;

        this.apiV1 = apiV1;
        this.apiV2 = apiV2;
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        apiV1.registerApi(jooby);
        apiV2.registerApi(jooby);
    }

    @MqRequest(endpoint = "FLUSH_CACHES")
    public void flushCaches(String unusedArg) {
        logger.info("Flushing caches");

        responseCache.flush();
        licenseService.flushCache();
    }


}
