package nu.marginalia.ping.model;

public sealed interface HistoricalAvailabilityData {
    record JustAvailability(String domain, DomainAvailabilityRecord record) implements HistoricalAvailabilityData {}
    record AvailabilityAndSecurity(String domain, DomainAvailabilityRecord availabilityRecord, DomainSecurityRecord securityRecord) implements HistoricalAvailabilityData {}
}
