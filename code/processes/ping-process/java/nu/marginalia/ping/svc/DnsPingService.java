package nu.marginalia.ping.svc;

import com.google.inject.Inject;
import nu.marginalia.ping.fetcher.PingDnsFetcher;
import nu.marginalia.ping.model.DomainDnsRecord;
import nu.marginalia.ping.model.WritableModel;
import nu.marginalia.ping.model.comparison.DnsRecordChange;
import nu.marginalia.ping.model.comparison.DomainDnsEvent;
import nu.marginalia.ping.util.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DnsPingService {
    private final PingDnsFetcher pingDnsFetcher;
    private final DomainDnsInformationFactory domainDnsInformationFactory;

    private static final Logger logger = LoggerFactory.getLogger(DnsPingService.class);

    @Inject
    public DnsPingService(PingDnsFetcher pingDnsFetcher,
                          DomainDnsInformationFactory domainDnsInformationFactory)
    {
        this.pingDnsFetcher = pingDnsFetcher;
        this.domainDnsInformationFactory = domainDnsInformationFactory;
    }

    public List<WritableModel> pingDomain(String rootDomain, @Nullable DomainDnsRecord oldRecord) throws SQLException {
        var digResult = pingDnsFetcher.dig(rootDomain);

        List<WritableModel> generatedRecords = new ArrayList<>();

        var newRecord = domainDnsInformationFactory.create(
                rootDomain,
                oldRecord != null ? oldRecord.dnsRootDomainId() : null,
                digResult
        );

        generatedRecords.add(newRecord);

        // If we have an old record, compare it with the new one and optionally generate a DomainDnsEvent
        if (oldRecord != null) {
            var changes = DnsRecordChange.between(newRecord, oldRecord);
            switch (changes) {
                case DnsRecordChange.None _ -> {}
                case DnsRecordChange.Changed changed -> {
                    logger.info("DNS record for {} changed: {}", newRecord.dnsRootDomainId(), changed);
                    generatedRecords.add(DomainDnsEvent.builder()
                            .rootDomainId(newRecord.dnsRootDomainId())
                            .nodeId(newRecord.nodeAffinity())
                            .tsChange(newRecord.tsLastUpdate())
                            .changeARecords(changed.aRecordsChanged())
                            .changeAaaaRecords(changed.aaaaRecordsChanged())
                            .changeCname(changed.cnameRecordChanged())
                            .changeMxRecords(changed.mxRecordsChanged())
                            .changeCaaRecords(changed.caaRecordsChanged())
                            .changeTxtRecords(changed.txtRecordsChanged())
                            .changeNsRecords(changed.nsRecordsChanged())
                            .changeSoaRecord(changed.soaRecordChanged())
                            .dnsSignatureBefore(new JsonObject<>(oldRecord))
                            .dnsSignatureAfter(new JsonObject<>(newRecord))
                            .build());
                }
            }
        }

        return generatedRecords;
    }

}
