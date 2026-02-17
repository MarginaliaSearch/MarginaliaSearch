package nu.marginalia.ping.model.comparison;

import nu.marginalia.ping.model.AvailabilityOutageType;
import nu.marginalia.ping.model.DomainAvailabilityRecord;

import java.util.Objects;

public sealed interface DomainAvailabilityChange {
    record None() implements DomainAvailabilityChange { }
    record UnavailableToAvailable() implements DomainAvailabilityChange { }
    record AvailableToUnavailable(AvailabilityOutageType outageType) implements DomainAvailabilityChange { }
    record OutageTypeChange(AvailabilityOutageType newOutageType) implements DomainAvailabilityChange { }

    static DomainAvailabilityChange between(
            DomainAvailabilityRecord oldStatus,
            DomainAvailabilityRecord newStatus
    ) {
        // Available -> Available is a no-up
        if (oldStatus.serverAvailable() && newStatus.serverAvailable()) {
            return new DomainAvailabilityChange.None();
        }

        // Down -> Up
        if (oldStatus.serverAvailable()) {
            return new DomainAvailabilityChange.AvailableToUnavailable(
                    AvailabilityOutageType.fromErrorClassification(newStatus.errorClassification())
            );
        }

        // Up -> Down
        if (newStatus.serverAvailable()) {
            return new DomainAvailabilityChange.UnavailableToAvailable();
        }

        // If unavailable -> unavailable, compare the reason
        var classOld = oldStatus.errorClassification();
        var classNew = newStatus.errorClassification();

        if (Objects.equals(classOld, classNew)) {
            return new DomainAvailabilityChange.None();
        }

        return new DomainAvailabilityChange.OutageTypeChange(
                AvailabilityOutageType.fromErrorClassification(newStatus.errorClassification())
        );

    }
}
