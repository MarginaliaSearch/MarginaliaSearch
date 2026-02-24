package nu.marginalia.scrapestopper;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ScrapeStopper {
    private final Duration viabilityPeriod = Duration.ofMinutes(5);

    private static final Logger logger = LoggerFactory.getLogger(ScrapeStopper.class);

    private final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<>();

    public ScrapeStopper() {
        Thread.ofVirtual().start(() -> {
            try {
                for (;;) {
                    Thread.sleep(Duration.ofMinutes(1));
                    tokens.values().removeIf(Token::isExpired);
                }
            }
            catch (InterruptedException ex) {
                logger.error("Sleep interrupted");
            }
        });
    }

    public String getToken(String zone,
                           String remoteIp,
                           Duration delay,
                           Duration validDuration,
                           int uses) {

        Instant validAfter = Instant.now().plus(delay);
        Instant validUntil = validAfter.plus(validDuration);

        Token token = new Token(validAfter, validUntil, remoteIp, uses);

        return assignSst(zone, token);
    }

    public String assignSst(String zone, Token token) {
        for (;;) {
            String maybeKey = String.format("%s-%08x",
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

    public TokenState validateToken(String tokenId, String remoteIp) {
        return Optional.ofNullable(tokenId)
                .map(tokens::get)
                .map(token -> token.validate(remoteIp, null))
                .orElse(TokenState.INVALID);
    }

    public TokenState validateToken(String tokenId, String remoteIp, String context) {
        return Optional.ofNullable(tokenId)
                .map(tokens::get)
                .map(token -> token.validate(remoteIp, context))
                .orElse(TokenState.INVALID);
    }
}


class Token {
    private final Instant validAfter;
    private final Instant validUntil;
    private final String remoteIp;
    private AtomicInteger remainingUses;

    private volatile Instant lastValidation;
    private volatile String lastContext;

    Token(Instant validAfter,
                  Instant validUntil,
                  String remoteIp,
                  int uses)
    {
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