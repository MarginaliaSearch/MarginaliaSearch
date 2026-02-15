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
        if (oldStatus.serverAvailable() == newStatus.serverAvailable()) {
            return new DomainAvailabilityChange.None();
        }

        if (oldStatus.serverAvailable()) {
            return new DomainAvailabilityChange.AvailableToUnavailable(
                    AvailabilityOutageType.fromErrorClassification(newStatus.errorClassification())
            );
        }

        if (newStatus.serverAvailable()) {
            return new DomainAvailabilityChange.UnavailableToAvailable();
        }
        else {
            var classOld = oldStatus.errorClassification();
            var classNew = newStatus.errorClassification();

            if (!Objects.equals(classOld, classNew)) {
                return new DomainAvailabilityChange.OutageTypeChange(
                        AvailabilityOutageType.fromErrorClassification(newStatus.errorClassification())
                );
            }
            else {
                return new DomainAvailabilityChange.None();
            }
        }
    }
}
