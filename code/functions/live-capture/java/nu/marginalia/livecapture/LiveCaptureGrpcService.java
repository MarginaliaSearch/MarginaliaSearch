package nu.marginalia.livecapture;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Named;
import nu.marginalia.api.livecapture.Empty;
import nu.marginalia.api.livecapture.LiveCaptureApiGrpc;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.DiscoverableService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/** GRPC service for on-demand capture of website screenshots */
public class LiveCaptureGrpcService
        extends LiveCaptureApiGrpc.LiveCaptureApiImplBase
        implements DiscoverableService
{

    private static final Logger logger = LoggerFactory.getLogger(LiveCaptureGrpcService.class);

    private final URI browserlessURI;
    private final boolean serviceEnabled;
    private final LinkedBlockingQueue<ScheduledScreenshot> requestedScreenshots = new LinkedBlockingQueue<>(128);
    private final HikariDataSource dataSource;

    record ScheduledScreenshot(int domainId) {}

    // Ensure that the service is only registered if it is enabled
    @Override
    public boolean shouldRegisterService() {
        return serviceEnabled;
    }

    @Inject
    public LiveCaptureGrpcService(HikariDataSource dataSource,
                                  @Named("browserless-uri") String browserlessAddress,
                                  @Named("browserless-agent-threads") int threads,
                                  ServiceConfiguration serviceConfiguration
                                  ) throws URISyntaxException {
        this.dataSource = dataSource;

        if (StringUtils.isEmpty(browserlessAddress) || serviceConfiguration.node() > 1) {
            logger.warn("Live capture service will not run");
            serviceEnabled = false;
            browserlessURI = null; // satisfy final
        }
        else {
            browserlessURI = new URI(browserlessAddress);
            serviceEnabled = true;

            for (int i = 0; i < threads; i++) {
                Thread.ofPlatform().daemon().name("Capture Agent " + i).start(new ScreenshotCaptureAgent());
            }
        }
    }

    public void requestScreengrab(nu.marginalia.api.livecapture.RpcDomainId request,
                                  StreamObserver<Empty> responseObserver)
    {
        if (serviceEnabled) {
            try (var conn = dataSource.getConnection()) {
                if (ScreenshotDbOperations.isEligibleForScreengrab(conn, request.getDomainId())) {
                    // may fail, we don't care about it
                    requestedScreenshots.offer(new ScheduledScreenshot(request.getDomainId()));
                }
            }
            catch (SQLException ex) {
                logger.error("Failed to check domain eligibility", ex);
            }
            finally {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        }
        else { // service is disabled
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    class ScreenshotCaptureAgent implements Runnable {

        // To prevent race conditions, we use this to lock domain ids that are being processed
        private static final ConcurrentHashMap<Integer, Boolean> domainIdsClaimed = new ConcurrentHashMap<>();

        @Override
        public void run() {
            try (BrowserlessClient client = new BrowserlessClient(browserlessURI)) {
                while (true) {
                    capture(client, requestedScreenshots.take());
                }
            } catch (InterruptedException e) {
                logger.error("Capture agent interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Capture agent failed", e);
            }
        }

        private void capture(BrowserlessClient client, ScheduledScreenshot scheduledScreenshot) {
            // Only one agent should capture a screenshot for a domain, so we skip if another agent has claimed it
            if (domainIdsClaimed.put(scheduledScreenshot.domainId(), Boolean.TRUE) != null) {
                return;
            }

            try (var conn = dataSource.getConnection()) {
                // Double check if the domain is still eligible for a screenshot
                if (!ScreenshotDbOperations.isEligibleForScreengrab(conn, scheduledScreenshot.domainId)) {
                    return;
                }

                var domainNameOpt = ScreenshotDbOperations.getDomainName(conn, scheduledScreenshot.domainId());
                if (domainNameOpt.isEmpty()) {
                    logger.error("Failed to get domain name for domain {}", scheduledScreenshot.domainId());
                }
                else {
                    EdgeDomain domain = domainNameOpt.get();
                    String domainNameStr = domain.toString();

                    if (!isValidDomainForCapture(domain)) {
                        ScreenshotDbOperations.flagDomainAsFetched(conn, domain);
                    }
                    else {
                        grab(client, conn, domain);
                    }
                }
            }
            catch (SQLException ex) {
                logger.error("Failed to check domain eligibility", ex);
            }
            finally {
                // Release the domain ID so that another agent can claim it
                // at this point we can assume the database will cover the
                // case where the domain is no longer eligible
                domainIdsClaimed.remove(scheduledScreenshot.domainId());
            }
        }

        private boolean isValidDomainForCapture(EdgeDomain domain) {
            String domainNameStr = domain.toString();
            String[] parts = domainNameStr.split("\\.");

            if (parts.length < 2) {
                return false;
            }

            if (Arrays.stream(parts).allMatch(StringUtils::isNumeric)) {
                // IP address
                return false;
            }

            return true;
        }

        private void grab(BrowserlessClient client, Connection conn, EdgeDomain domain) {
            try {
                logger.info("Capturing {}", domain);

                byte[] pngBytes = client.screenshot(domain.toRootUrlHttps().toString(),
                        BrowserlessClient.GotoOptions.defaultValues(),
                        BrowserlessClient.ScreenshotOptions.defaultValues());
                if (pngBytes.length > 0) {
                    ScreenshotDbOperations.uploadScreenshot(conn, domain, pngBytes);
                }
            } catch (Exception e) {
                ScreenshotDbOperations.flagDomainAsFetched(conn, domain);
            }
        }
    }

}
