package nu.marginalia.ping.ssl;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class AIAExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AIAExtractor.class);

    public static List<String> getCaIssuerUrls(X509Certificate certificate) {
        List<String> caIssuerUrls = new ArrayList<>();

        try {
            // Get the AIA extension value
            byte[] aiaExtensionValue = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());
            if (aiaExtensionValue == null) {
                logger.warn("No AIA extension found");
                return caIssuerUrls;
            }

            // Parse the extension - first unwrap the OCTET STRING
            ASN1OctetString octetString = ASN1OctetString.getInstance(aiaExtensionValue);
            ASN1Primitive aiaObj = ASN1Primitive.fromByteArray(octetString.getOctets());

            // Parse as AuthorityInformationAccess
            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(aiaObj);

            if (aia != null) {
                AccessDescription[] accessDescriptions = aia.getAccessDescriptions();

                for (AccessDescription accessDesc : accessDescriptions) {
                    // Check if this is a CA Issuers access method
                    if (X509ObjectIdentifiers.id_ad_caIssuers.equals(accessDesc.getAccessMethod())) {
                        GeneralName accessLocation = accessDesc.getAccessLocation();

                        // Check if it's a URI
                        if (accessLocation.getTagNo() == GeneralName.uniformResourceIdentifier) {
                            String url = accessLocation.getName().toString();
                            caIssuerUrls.add(url);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error parsing AIA extension: {}", e.getMessage());
        }

        return caIssuerUrls;
    }

}