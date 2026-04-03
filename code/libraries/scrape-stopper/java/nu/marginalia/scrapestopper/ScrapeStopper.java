package nu.marginalia.scrapestopper;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ScrapeStopper {
    private final Duration viabilityPeriod = Duration.ofMinutes(5);

    private static final Logger logger = LoggerFactory.getLogger(ScrapeStopper.class);

    private final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ValidationRate> validationRatePerZone = new ConcurrentHashMap<>();

    public ScrapeStopper() {
        Thread.ofVirtual().start(() -> {
            try {
                for (;;) {
                    Thread.sleep(Duration.ofMinutes(1));

                    tokens.values().removeIf(Token::isExpired);
                    validationRatePerZone.values().forEach(ValidationRate::updateTarget);
                }
            }
            catch (InterruptedException ex) {
                logger.error("Sleep interrupted");
            }
        });
    }

    public String getToken(String zone,
                           String remoteIp,
                           Duration validDuration,
                           int uses) {

        Duration delay = getValidationRate(zone).getDelay();

        Instant validAfter = Instant.now().plus(delay);
        Instant validUntil = validAfter.plus(validDuration);

        Token token = new Token(zone, validAfter, validUntil, remoteIp, uses);

        return assignSst(zone, token);
    }

    public String assignSst(String zone, Token token) {
        for (;;) {
            String maybeKey = String.format("%s-%016x",
                    zone,
                    ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE));

            if (tokens.put(maybeKey, token) == null) {
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
        return validationRatePerZone.computeIfAbsent(zone, z -> new ValidationRate(100));
    }

}


class Token {
    public final String zone;

    private final Instant validAfter;
    private final Instant validUntil;
    private final String remoteIp;
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
    }

    public ScrapeStopper.TokenState validate(String remoteIp, String context) {
        if (!Objects.equals(remoteIp, this.remoteIp))
            return ScrapeStopper.TokenState.INVALID;

        if (context != null && Objects.equals(lastContext,context))
            return ScrapeStopper.TokenState.VALIDATED;

        if (Instant.now().isBefore(validAfter))
            return ScrapeStopper.TokenState.EARLY;

        if (Instant.now().isAfter(validUntil))
            return ScrapeStopper.TokenState.INVALID;

        var lastValidation = this.lastValidation;

        if (lastValidation != null) {
            Duration timeSinceValidation = Duration.between(lastValidation, Instant.now());

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

    public Duration timeUntilValid() {
        return Duration.between(Instant.now(), validAfter);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(validAfter) && remainingUses.getAcquire() <= 0;
    }
}

class ValidationRate {
    private final int maxSize;

    private double target;
    private volatile double delay;
    private double delayMin;
    private double delayMax;

    private final LinkedList<Instant> validations = new LinkedList<>();

    public ValidationRate(int maxSize) {
        this.maxSize = maxSize;

        this.target = 2.0;
        this.delay = 1.;
        this.delayMin = 1.0;
        this.delayMax = 5.;
    }

    public synchronized void register() {
        validations.addLast(Instant.now());

        if (validations.size() > maxSize) {
            validations.removeFirst();
        }
    }

    public synchronized void updateTarget() {
        if (validations.size() < maxSize/2) {
            return;
        }

        long millisBetween = Duration.between(validations.getFirst(), validations.getLast()).toMillis();

        double secs = millisBetween / 1000.;
        double interval = secs / (validations.size()-1);

        // Delay and target rate accidentally is of the same order of magnitude [1...5]
        // which makes this a bit easier

        double delta = target - interval;

        delay = Math.clamp(delay + delta, delayMin, delayMax);
    }

    public Duration getDelay() {
        return Duration.ofMillis((long)(1000*delay));
    }

}