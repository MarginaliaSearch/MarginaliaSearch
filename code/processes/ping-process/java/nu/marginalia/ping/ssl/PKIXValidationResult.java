package nu.marginalia.ping.ssl;

import java.security.cert.CertPath;
import java.security.cert.PKIXCertPathValidatorResult;
import java.util.Set;

public record PKIXValidationResult(boolean isValid, String errorMessage,
                                   Set<PkixValidationError> errors,
                                   PKIXCertPathValidatorResult pkixResult,
                                   CertPath validatedPath,
                                   Set<String> criticalExtensions,
                                   boolean hostnameValid)
{
}
