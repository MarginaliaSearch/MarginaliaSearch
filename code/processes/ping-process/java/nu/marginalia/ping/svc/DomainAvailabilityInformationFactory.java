package nu.marginalia.ping.svc;

import com.google.inject.Inject;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.geoip.sources.AsnTable;
import nu.marginalia.ping.BackoffStrategy;
import nu.marginalia.ping.fetcher.response.HttpResponse;
import nu.marginalia.ping.fetcher.response.HttpsResponse;
import nu.marginalia.ping.model.DomainAvailabilityRecord;
import nu.marginalia.ping.model.ErrorClassification;
import nu.marginalia.ping.model.HttpSchema;
import nu.marginalia.ping.ssl.CertificateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class DomainAvailabilityInformationFactory {
    private static final Logger logger = LoggerFactory.getLogger(DomainAvailabilityInformationFactory.class);

    private final GeoIpDictionary geoIpDictionary;
    private final BackoffStrategy backoffStrategy;

    @Inject
    public DomainAvailabilityInformationFactory(GeoIpDictionary geoIpDictionary,
                                                BackoffStrategy backoffStrategy) {
        this.geoIpDictionary = geoIpDictionary;
        this.backoffStrategy = backoffStrategy;
    }


    public DomainAvailabilityRecord createError(int domainId,
                                                      int nodeId,
                                                      @Nullable DomainAvailabilityRecord previousRecord,
                                                      ErrorClassification errorClassification,
                                                      @Nullable String errorMessage
                                                      ) {

        Duration currentRefreshInterval = previousRecord != null ? previousRecord.backoffFetchInterval() : null;
        int errorCount = previousRecord != null ? previousRecord.backoffConsecutiveFailures() : 0;
        Instant lastAvailable = previousRecord != null ? previousRecord.tsLastAvailable() : null;

        Duration refreshInterval = backoffStrategy.getUpdateTime(
                currentRefreshInterval,
                errorClassification,
                errorCount);

        return DomainAvailabilityRecord.builder()
                .domainId(domainId)
                .nodeId(nodeId)
                .serverAvailable(false)
                .errorClassification(errorClassification)
                .errorMessage(errorMessage)
                .tsLastAvailable(lastAvailable)
                .tsLastPing(Instant.now())
                .tsLastError(Instant.now())
                .nextScheduledUpdate(Instant.now().plus(refreshInterval))
                .backoffFetchInterval(refreshInterval)
                .backoffConsecutiveFailures(errorCount+1)
                .build();
    }

    public DomainAvailabilityRecord createHttpResponse(int domainId,
                                                       int nodeId,
                                                       @Nullable InetAddress address,
                                                       @Nullable DomainAvailabilityRecord previousRecord,
                                                       HttpResponse rsp) {

        final boolean isAvailable;
        final Instant now = Instant.now();
        final Instant lastAvailable;
        final Instant lastError;
        final ErrorClassification errorClassification;

        if (rsp.httpStatus() >= 400) {
            isAvailable = false;
            lastError = now;
            lastAvailable = previousRecord != null ? previousRecord.tsLastAvailable() : null;
            errorClassification = ErrorClassification.HTTP_SERVER_ERROR;
        } else {
            isAvailable = true;
            lastAvailable = now;
            lastError = previousRecord != null ? previousRecord.tsLastError() : null;
            errorClassification = ErrorClassification.NONE;
        }

        return DomainAvailabilityRecord.builder()
                .domainId(domainId)
                .nodeId(nodeId)
                .serverAvailable(isAvailable)
                .serverIp(address != null ? address.getAddress() : null)
                .serverIpAsn(getAsn(address))
                .httpSchema(HttpSchema.HTTP)
                .httpLocation(rsp.headers().getFirst("Location"))
                .httpStatus(rsp.httpStatus())
                .errorClassification(errorClassification)
                .httpResponseTime(rsp.httpResponseTime())
                .httpEtag(rsp.headers().getFirst("ETag"))
                .httpLastModified(rsp.headers().getFirst("Last-Modified"))
                .tsLastPing(now)
                .tsLastAvailable(lastAvailable)
                .tsLastError(lastError)
                .nextScheduledUpdate(now.plus(backoffStrategy.getOkInterval()))
                .backoffFetchInterval(backoffStrategy.getOkInterval())
                .build();

    }

    private Integer getAsn(@Nullable InetAddress address) {
        if (address == null) {
            return null;
        }
        // Placeholder for ASN lookup logic
        return geoIpDictionary.getAsnInfo(address).map(AsnTable.AsnInfo::asn).orElse(null);
    }


    public DomainAvailabilityRecord createHttpsResponse(int domainId,
                                                        int nodeId,
                                                        @Nullable InetAddress address,
                                                        @Nullable DomainAvailabilityRecord previousRecord,
                                                        CertificateValidator.ValidationResult validationResult,
                                                        HttpsResponse rsp) {
        Instant updateTime;

        if (validationResult.isValid()) {
            updateTime = sslCertInformedUpdateTime((X509Certificate[]) rsp.sslCertificates());
        }
        else {
            updateTime = Instant.now().plus(backoffStrategy.getOkInterval());
        }

        final boolean isAvailable;
        final Instant now = Instant.now();
        final Instant lastAvailable;
        final Instant lastError;
        final ErrorClassification errorClassification;

        if (!validationResult.isValid()) {
            isAvailable = false;
            lastError = now;
            lastAvailable = previousRecord != null ? previousRecord.tsLastAvailable() : null;
            errorClassification = ErrorClassification.SSL_ERROR;
        } else if (rsp.httpStatus() >= 400) {
            isAvailable = false;
            lastError = now;
            lastAvailable = previousRecord != null ? previousRecord.tsLastAvailable() : null;
            errorClassification = ErrorClassification.HTTP_SERVER_ERROR;
        } else {
            isAvailable = true;
            lastAvailable = Instant.now();
            lastError = previousRecord != null ? previousRecord.tsLastError() : null;
            errorClassification = ErrorClassification.NONE;
        }

        return DomainAvailabilityRecord.builder()
                .domainId(domainId)
                .nodeId(nodeId)
                .serverAvailable(isAvailable)
                .serverIp(address != null ? address.getAddress() : null)
                .serverIpAsn(getAsn(address))
                .httpSchema(HttpSchema.HTTPS)
                .httpLocation(rsp.headers().getFirst("Location"))
                .httpStatus(rsp.httpStatus())
                .errorClassification(errorClassification)
                .httpResponseTime(rsp.httpResponseTime()) // Placeholder, actual timing not implemented
                .httpEtag(rsp.headers().getFirst("ETag"))
                .httpLastModified(rsp.headers().getFirst("Last-Modified"))
                .tsLastPing(now)
                .tsLastError(lastError)
                .tsLastAvailable(lastAvailable)
                .nextScheduledUpdate(updateTime)
                .backoffFetchInterval(backoffStrategy.getOkInterval())
                .build();
    }

    /**
     * Calculates the next update time for a domain based on the SSL certificate's expiry date.
     * If the certificate is valid for more than 5 days, it will check 3 days before expiry.
     * If it expires in less than 5 days, it will check just after expiry.
     *
     * @param certificates The SSL certificates associated with the domain.
     * @return The next update time as an Instant.
     */
    private Instant sslCertInformedUpdateTime(X509Certificate[] certificates) {
        Instant now = Instant.now();
        Instant normalUpdateTime = now.plus(backoffStrategy.getOkInterval());

        if (certificates == null || certificates.length == 0) {
            return normalUpdateTime;
        }

        try {
            X509Certificate cert = certificates[0];

            // Use the first certificate's notAfter date as the update time
            Instant certExpiry = certificates[0].getNotAfter().toInstant();

            // If the certificate expires in less than 3 days, we'll check just after expiry
            if (Duration.between(Instant.now(), cert.getNotAfter().toInstant()).toDays() < 3) {
                return minDate(normalUpdateTime, certExpiry.plus(3, ChronoUnit.MINUTES));
            }
            else {
                // If the certificate is valid for more than 3 days, we'll check 3 days before expiry for renewal
                return minDate(normalUpdateTime, certExpiry.minus(3, ChronoUnit.DAYS));
            }

        } catch (Exception e) {
            logger.warn("Failed to get certificate expiry date: {}", e.getMessage());
        }

        return normalUpdateTime;
    }

    private Instant minDate(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }


}
