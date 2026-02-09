package nu.marginalia.ping.fetcher.response;

import org.apache.hc.core5.http.ProtocolVersion;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public sealed interface PingRequestResponse
        permits HttpResponse, HttpsResponse, TimeoutResponse, ConnectionError, ProtocolError, UnknownHostError {
    static PingRequestResponse of(ProtocolVersion version, int httpStatus, Map<String, List<String>> headers, Duration time, SSLSession sslSession) throws SSLPeerUnverifiedException {

        if (sslSession == null) {
            return new HttpResponse(version.toString(), httpStatus,new Headers(headers), time);
        } else {
            return new HttpsResponse(version.toString(), httpStatus, new Headers(headers), sslSession.getPeerCertificates(), new SslMetadata(sslSession), time);
        }
    }

}
