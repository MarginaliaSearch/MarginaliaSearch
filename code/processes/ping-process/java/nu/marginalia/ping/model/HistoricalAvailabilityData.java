package nu.marginalia.ping.model;

public sealed interface HistoricalAvailabilityData {
    public String domain();
    record JustDomainReference(DomainReference domainReference) implements HistoricalAvailabilityData {
        @Override
        public String domain() {
            return domainReference.domainName();
        }
    }
    record JustAvailability(String domain, DomainAvailabilityRecord record) implements HistoricalAvailabilityData {}
    record AvailabilityAndSecurity(String domain, DomainAvailabilityRecord availabilityRecord, DomainSecurityRecord securityRecord) implements HistoricalAvailabilityData {}
}
