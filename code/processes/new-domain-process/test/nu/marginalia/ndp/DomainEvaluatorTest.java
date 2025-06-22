package nu.marginalia.ndp;

import nu.marginalia.coordination.LocalDomainCoordinator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEvaluatorTest {

    @Tag("flaky") // Exclude from CI runs due to potential network issues
    @Test
    public void testSunnyDay() throws NoSuchAlgorithmException, KeyManagementException {
        DomainEvaluator evaluator = new DomainEvaluator(new LocalDomainCoordinator());

        // Should be a valid domain
        assertTrue(evaluator.evaluateDomain("www.marginalia.nu"));

        // Should be a redirect to www.marginalia.nu
        assertFalse(evaluator.evaluateDomain("memex.marginalia.nu"));

        // Should fail on Anubis
        assertFalse(evaluator.evaluateDomain("marginalia-search.com"));
    }
}