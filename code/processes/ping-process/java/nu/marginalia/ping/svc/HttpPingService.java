package nu.marginalia.ping.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.coordination.DomainCoordinator;
import nu.marginalia.ping.fetcher.PingHttpFetcher;
import nu.marginalia.ping.fetcher.response.*;
import nu.marginalia.ping.model.*;
import nu.marginalia.ping.model.comparison.DomainAvailabilityChange;
import nu.marginalia.ping.model.comparison.SecurityInformationChange;
import nu.marginalia.ping.ssl.CustomPKIXValidator;
import nu.marginalia.ping.ssl.PKIXValidationResult;
import nu.marginalia.ping.util.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class HttpPingService {

    private final DomainCoordinator domainCoordinator;
    private final PingHttpFetcher pingHttpFetcher;

    private final DomainAvailabilityInformationFactory domainAvailabilityInformationFactory;
    private final DomainSecurityInformationFactory domainSecurityInformationFactory;

    private static final Logger logger = LoggerFactory.getLogger(HttpPingService.class);
    CustomPKIXValidator validator;

    @Inject
    public HttpPingService(
            DomainCoordinator domainCoordinator,
            PingHttpFetcher pingHttpFetcher,
            DomainAvailabilityInformationFactory domainAvailabilityInformationFactory,
            DomainSecurityInformationFactory domainSecurityInformationFactory) throws Exception {
        this.domainCoordinator = domainCoordinator;
        this.pingHttpFetcher = pingHttpFetcher;
        this.domainAvailabilityInformationFactory = domainAvailabilityInformationFactory;
        this.domainSecurityInformationFactory = domainSecurityInformationFactory;
        this.validator = new CustomPKIXValidator();
    }

    private int compareInetAddresses(InetAddress a, InetAddress b) {
        byte[] aBytes = a.getAddress();
        byte[] bBytes = b.getAddress();

        int diff1 = Integer.compare(aBytes.length, bBytes.length);
        if (diff1 != 0) {
            return diff1;
        }

        return Arrays.compare(aBytes, bBytes);
    }

    public List<WritableModel> pingDomain(DomainReference domainReference,
                           @Nullable DomainAvailabilityRecord oldPingStatus,
                           @Nullable DomainSecurityRecord oldSecurityInformation) throws SQLException, InterruptedException {

        // First we figure out if the domain maps to an IP address

        List<WritableModel> generatedRecords = new ArrayList<>();

        List<InetAddress> ipAddress = getIpAddress(domainReference.domainName());
        PingRequestResponse result;

        if (ipAddress.isEmpty()) {
            result = new UnknownHostError();
        } else {
            // lock the domain to prevent concurrent pings
            try (var _ = domainCoordinator.lockDomain(domainReference.asEdgeDomain())) {
                String url = "https://" + domainReference.domainName() + "/";
                String alternateUrl = "http://" + domainReference.domainName() + "/";

                result = pingHttpFetcher.fetchUrl(url, Method.HEAD, null, null);

                if (result instanceof HttpsResponse response && shouldTryGET(response.httpStatus())) {
                    sleep(Duration.ofSeconds(2));
                    result = pingHttpFetcher.fetchUrl(url, Method.GET, null, null);
                } else if (result instanceof ConnectionError) {
                    var result2 = pingHttpFetcher.fetchUrl(alternateUrl, Method.HEAD, null, null);
                    if (!(result2 instanceof ConnectionError)) {
                        result = result2;
                    }
                    if (result instanceof HttpResponse response && shouldTryGET(response.httpStatus())) {
                        sleep(Duration.ofSeconds(2));
                        result = pingHttpFetcher.fetchUrl(alternateUrl, Method.GET, null, null);
                    }
                }

                // Add a grace sleep before we yield the semaphore, so that another thread doesn't
                // immediately hammer the same domain after it's released.
                sleep(Duration.ofSeconds(1));
            }
        }


        // For a consistent picture, we always use the "binary-smallest" IP IPv4 address returned by InetAddress.getAllByName,
        // for resolving ASN and similar information.
        final InetAddress lowestIpAddress = ipAddress.stream().min(this::compareInetAddresses).orElse(null);


        final DomainAvailabilityRecord newPingStatus;
        final DomainSecurityRecord newSecurityInformation;

        switch (result) {
            case UnknownHostError rsp -> {
                newPingStatus = domainAvailabilityInformationFactory.createError(
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        oldPingStatus,
                        ErrorClassification.DNS_ERROR,
                        null);
                newSecurityInformation = null;
            }
            case ConnectionError rsp -> {
                newPingStatus = domainAvailabilityInformationFactory.createError(
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        oldPingStatus,
                        ErrorClassification.CONNECTION_ERROR,
                        rsp.errorMessage());
                newSecurityInformation = null;
            }
            case TimeoutResponse rsp -> {
                newPingStatus = domainAvailabilityInformationFactory.createError(
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        oldPingStatus,
                        ErrorClassification.TIMEOUT,
                        null);
                newSecurityInformation = null;
            }
            case ProtocolError rsp -> {
                newPingStatus = domainAvailabilityInformationFactory.createError(
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        oldPingStatus,
                        ErrorClassification.HTTP_CLIENT_ERROR,
                        null);
                newSecurityInformation = null;
            }
            case HttpResponse httpResponse -> {
                newPingStatus = domainAvailabilityInformationFactory.createHttpResponse(
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        lowestIpAddress,
                        oldPingStatus,
                        httpResponse);

                newSecurityInformation = domainSecurityInformationFactory.createHttpSecurityInformation(
                        httpResponse,
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        newPingStatus.asn()
                );
            }
            case HttpsResponse httpsResponse -> {
                PKIXValidationResult validationResult = validator.validateCertificateChain(domainReference.domainName(), (X509Certificate[]) httpsResponse.sslCertificates());

                newPingStatus = domainAvailabilityInformationFactory.createHttpsResponse(
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        lowestIpAddress,
                        oldPingStatus,
                        validationResult,
                        httpsResponse);

                newSecurityInformation = domainSecurityInformationFactory.createHttpsSecurityInformation(
                        httpsResponse,
                        validationResult,
                        domainReference.domainId(),
                        domainReference.nodeId(),
                        newPingStatus.asn()
                );
            }
        }

        // We always write the new ping status, even if it is the same as the old one.
        generatedRecords.add(newPingStatus);

        if (newSecurityInformation != null) {
            generatedRecords.add(newSecurityInformation);
        }

        if (oldPingStatus != null && newPingStatus != null) {
            comparePingStatuses(generatedRecords, oldPingStatus, newPingStatus);
        }
        if (oldSecurityInformation != null && newSecurityInformation != null) {
            compareSecurityInformation(generatedRecords,
                    oldSecurityInformation, oldPingStatus,
                    newSecurityInformation, newPingStatus);
        }

        return generatedRecords;
    }

    private boolean shouldTryGET(int statusCode) {
        if (statusCode < 400) {
            return false;
        }
        if (statusCode == 429) { // Too many requests, we should not retry with GET
            return false;
        }

        // For all other status codes, we can try a GET request, as many severs do not
        // cope with HEAD requests properly.

        return statusCode < 600;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            logger.warn("Sleep interrupted", e);
        }
    }

    private void comparePingStatuses(List<WritableModel> generatedRecords,
                                     DomainAvailabilityRecord oldPingStatus,
                                     DomainAvailabilityRecord newPingStatus) {

        var change = DomainAvailabilityChange.between(oldPingStatus, newPingStatus);
        switch (change) {
            case DomainAvailabilityChange.None none -> {}
            case DomainAvailabilityChange.AvailableToUnavailable(AvailabilityOutageType outageType) -> {
                generatedRecords.add(new DomainAvailabilityEvent(
                        oldPingStatus.domainId(),
                        oldPingStatus.nodeId(),
                        false,
                        outageType,
                        newPingStatus.httpStatus(),
                        newPingStatus.errorMessage(),
                        newPingStatus.tsLastPing()
                ));
            }
            case DomainAvailabilityChange.UnavailableToAvailable _ -> {
                generatedRecords.add(
                        new DomainAvailabilityEvent(
                                oldPingStatus.domainId(),
                                oldPingStatus.nodeId(),
                                true,
                                AvailabilityOutageType.NONE,
                                newPingStatus.httpStatus(),
                                newPingStatus.errorMessage(),
                                newPingStatus.tsLastPing()
                        )
                );
            }
            case DomainAvailabilityChange.OutageTypeChange(AvailabilityOutageType newOutageType) -> {
                generatedRecords.add(
                        new DomainAvailabilityEvent(
                                oldPingStatus.domainId(),
                                oldPingStatus.nodeId(),
                                false,
                                newOutageType,
                                newPingStatus.httpStatus(),
                                newPingStatus.errorMessage(),
                                newPingStatus.tsLastPing()
                        )
                );
            }
        }
    }


    private void compareSecurityInformation(List<WritableModel> generatedRecords,
                                            DomainSecurityRecord oldSecurityInformation,
                                            DomainAvailabilityRecord oldPingStatus,
                                            DomainSecurityRecord newSecurityInformation,
                                            DomainAvailabilityRecord newPingStatus
                                            ) {
        var change = SecurityInformationChange.between(oldSecurityInformation, oldPingStatus, newSecurityInformation, newPingStatus);

        if (!change.isChanged())
            return;

        generatedRecords.add(new DomainSecurityEvent(
                newSecurityInformation.domainId(),
                newSecurityInformation.nodeId(),
                newSecurityInformation.tsLastUpdate(),
                change.isAsnChanged(),
                change.isCertificateFingerprintChanged(),
                change.isCertificateProfileChanged(),
                change.isCertificateSanChanged(),
                change.isCertificatePublicKeyChanged(),
                change.isCertificateSerialNumberChanged(),
                change.isCertificateIssuerChanged(),
                change.oldCertificateTimeToExpiry(),
                change.isSecurityHeadersChanged(),
                change.isIpAddressChanged(),
                change.isSoftwareHeaderChanged(),
                new JsonObject<>(oldSecurityInformation),
                new JsonObject<>(newSecurityInformation)
        ));

    }


    List<InetAddress> getIpAddress(String domainName) {
        try {
            return Arrays.asList(InetAddress.getAllByName(domainName));
        } catch (UnknownHostException e) {
            // Handle the exception, e.g., log it or return an empty Optional
            return List.of();
        }
    }
}
