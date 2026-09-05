package nu.marginalia.scrapestopper;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Singleton
public class ScrapeStopper {
    private static final Logger logger = LoggerFactory.getLogger(ScrapeStopper.class);

    private final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Token> tokensByIpZone = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ValidationRate> validationRatePerZone = new ConcurrentHashMap<>();

    public ScrapeStopper() {
        Thread.ofVirtual().start(() -> {
            try {
                for (;;) {
                    Thread.sleep(Duration.ofMinutes(1));

                    tokens.values().removeIf(Token::isExpired);
                    tokensByIpZone.values().removeIf(Token::isExpired);
                    validationRatePerZone.values().forEach(ValidationRate::updateDelay);
                }
            }
            catch (InterruptedException ex) {
                logger.error("Sleep interrupted");
            }
        });
    }

    public String getToken(String zone,
                           String remoteIp,
                           Duration validDuration) {

        ValidationRate validationRate = getValidationRate(zone);

        Duration delay = validationRate.getDelay();

        Instant validAfter = Instant.now().plus(delay);
        Instant validUntil = validAfter.plus(validDuration);

        int uses = 10 + (int) (40 * (1 - validationRate.getStrain()));

        Token token = new Token(zone, validAfter, validUntil, remoteIp, uses);

        return assignSst(zone, token);
    }

    public String assignSst(String zone, Token token) {
        // If this ip+zone already has a token, then we return that.
        // This will slightly hurt people sharing the same IP, but not by much,
        // and will make token storage more inconvenient.

        String ipZone = token.remoteIp + "-" + zone;
        Token existingTokenForIp = tokensByIpZone.get(ipZone);

        if (existingTokenForIp != null && !existingTokenForIp.isExpired()) {
            String sst = existingTokenForIp.sst;

            if (sst != null) {
                return existingTokenForIp.sst;
            }
        }

        for (;;) {
            String maybeKey = String.format("%s-%016x",
                    zone,
                    ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE));

            token.sst = maybeKey;
            if (tokens.put(maybeKey, token) == null) {
                // There is some minor raceyness here,
                // but it shouldn't really affect realistic paths
                tokensByIpZone.put(ipZone, token);

                return maybeKey;
            }
        }
    }

    /** Move the token assocated with the provided sst to a new sst.
     *
     * This operation may fail through, and if that happens, it should be interpreted
     * as though the SST was invalid.
     *
     */
    public Optional<String> relocateToken(String sst, String zone) {
        Token token;

        if (null == (token = tokens.remove(sst)))
            return Optional.empty();

        tokensByIpZone.remove(token.remoteIp + "-" + zone, token);

        return Optional.of(assignSst(zone, token));
    }

    public Optional<Duration> getRemaining(String tokenId) {
        return Optional.ofNullable(tokens.get(tokenId)).map(Token::timeUntilValid);
    }



    public enum TokenState {
        EARLY,
        VALIDATED,
        INVALID
    };

    public TokenState validateToken(String tokenId, String remoteIp, String context) {
        if (null == tokenId)
            return TokenState.INVALID;

        Token token = tokens.get(tokenId);

        if (null == token)
            return TokenState.INVALID;

        TokenState state = token.validate(remoteIp, context);
        if (state == TokenState.VALIDATED) {
            getValidationRate(token.zone).register();
        }
        return state;
    }

    private ValidationRate getValidationRate(String zone) {
        return validationRatePerZone.computeIfAbsent(zone, z -> new ValidationRate(InstantSource.system()));
    }

    public boolean isStrained(String zone, double threshold) {
        return getValidationRate(zone).isStrained(threshold);
    }

}


class Token {
    public final String zone;
    @Nullable
    public String sst;

    private final Instant validAfter;
    private final Instant validUntil;
    public final String remoteIp;
    private AtomicInteger remainingUses;

    private volatile Instant lastValidation;
    private volatile String lastContext;

    Token(String zone,
          Instant validAfter,
          Instant validUntil,
          String remoteIp,
          int uses)
    {
        this.zone = zone;
        this.validAfter = validAfter;
        this.validUntil = validUntil;
        this.remoteIp = remoteIp;
        this.remainingUses = new AtomicInteger(uses);
        this.sst = null; // will be assigned later
    }

    public ScrapeStopper.TokenState validate(String remoteIp, String context) {
        if (!Objects.equals(remoteIp, this.remoteIp))
            return ScrapeStopper.TokenState.INVALID;

        if (Instant.now().isBefore(validAfter))
            return ScrapeStopper.TokenState.EARLY;

        if (Instant.now().isAfter(validUntil))
            return ScrapeStopper.TokenState.INVALID;

        // Short circuit
        if (context != null && Objects.equals(lastContext,context))
            return ScrapeStopper.TokenState.VALIDATED;

        var lastValidation = this.lastValidation;

        if (lastValidation == null) {

            // Token validation is suspiciously delayed
            if (Instant.now().isAfter(validAfter.plusSeconds(5))) {
                remainingUses.set(0);
                return ScrapeStopper.TokenState.INVALID;
            }
        }
        else { // We have a previous validation time
            Duration timeSinceValidation = Duration.between(lastValidation, Instant.now());

            // Requests are too frequent
            if (timeSinceValidation.minusSeconds(1).isNegative()) {
                remainingUses.set(0);

                return ScrapeStopper.TokenState.INVALID;
            }
        }

        if (remainingUses.decrementAndGet() <= 0)
            return ScrapeStopper.TokenState.INVALID;

        this.lastContext = context;
        this.lastValidation = Instant.now();

        return ScrapeStopper.TokenState.VALIDATED;
    }

    public boolean hasSameRemoteIp(Token otherToken) {
        return Objects.equals(otherToken.remoteIp, this.remoteIp);
    }

    public Duration timeUntilValid() {
        // assignSst may hand out a token that is already past validAfter,
        // so clamp to zero to avoid reporting a negative wait
        Duration remaining = Duration.between(Instant.now(), validAfter);

        if (remaining.isNegative())
            return Duration.ZERO;

        return remaining;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(validUntil) || remainingUses.getAcquire() <= 0;
    }

}

class ValidationRate {
    private static final double DELAY_MIN = 1.0;
    private static final double DELAY_MAX = 5.0;

    private final InstantSource clock;
    private final LongAdder validationCount = new LongAdder();
    private Instant lastUpdate;

    private volatile double delay = DELAY_MIN;

    public ValidationRate(InstantSource clock) {
        this.clock = clock;
        this.lastUpdate = clock.instant();
    }

    public void register() {
        validationCount.increment();
    }

    public synchronized void updateDelay() {
        Instant now = clock.instant();
        double elapsedSecs = Duration.between(lastUpdate, now).toMillis() / 1000.;
        lastUpdate = now;

        if (elapsedSecs <= 0)
            return;

        double measuredRps = validationCount.sumThenReset() / elapsedSecs;

        delay = Math.clamp(measuredRps, DELAY_MIN, DELAY_MAX);
    }

    public Duration getDelay() {
        return Duration.ofMillis((long)(1000*delay));
    }

    public boolean isStrained(double threshold) {
        return getStrain() > threshold;
    }

    public double getStrain() {
        return (delay - DELAY_MIN) / (DELAY_MAX - DELAY_MIN);
    }

}
