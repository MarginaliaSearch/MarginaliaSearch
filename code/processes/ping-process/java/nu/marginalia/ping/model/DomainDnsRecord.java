package nu.marginalia.ping.model;

import com.google.gson.Gson;
import nu.marginalia.model.gson.GsonFactory;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

public record DomainDnsRecord(
        Integer dnsRootDomainId,
        String rootDomainName,
        int nodeAffinity,
        DnsRecordsReference aRecords,
        DnsRecordsReference aaaaRecords,
        @Nullable String cnameRecord,
        DnsRecordsReference mxRecords,
        DnsRecordsReference caaRecords,
        DnsRecordsReference txtRecords,
        DnsRecordsReference nsRecords,
        @Nullable String soaRecord,
        Instant tsLastUpdate,
        Instant tsNextScheduledUpdate,
        int dnsCheckPriority)
    implements WritableModel
{

    public DomainDnsRecord(ResultSet rs) throws SQLException {
        this(
                rs.getObject("DNS_ROOT_DOMAIN_ID", Integer.class),
                rs.getString("ROOT_DOMAIN_NAME"),
                rs.getInt("NODE_AFFINITY"),
                DnsRecordsReference.decode(rs.getString("DNS_A_RECORDS")),
                DnsRecordsReference.decode(rs.getString("DNS_AAAA_RECORDS")),
                rs.getString("DNS_CNAME_RECORD"),
                DnsRecordsReference.decode(rs.getString("DNS_MX_RECORDS")),
                DnsRecordsReference.decode(rs.getString("DNS_CAA_RECORDS")),
                DnsRecordsReference.decode(rs.getString("DNS_TXT_RECORDS")),
                DnsRecordsReference.decode(rs.getString("DNS_NS_RECORDS")),
                rs.getString("DNS_SOA_RECORD"),
                rs.getObject("TS_LAST_DNS_CHECK", Instant.class),
                rs.getObject("TS_NEXT_DNS_CHECK", Instant.class),
                rs.getInt("DNS_CHECK_PRIORITY")
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Instant nextUpdateTime() {
        return tsNextScheduledUpdate;
    }

    @Override
    public void write(Connection connection) throws SQLException {

        if (dnsRootDomainId() != null) {
            update(connection);
            return;
        }

        try (var ps = connection.prepareStatement("""
            REPLACE INTO DOMAIN_DNS_INFORMATION (
                ROOT_DOMAIN_NAME,
                NODE_AFFINITY,
                DNS_A_RECORDS,
                DNS_AAAA_RECORDS,
                DNS_CNAME_RECORD,
                DNS_MX_RECORDS,
                DNS_CAA_RECORDS,
                DNS_TXT_RECORDS,
                DNS_NS_RECORDS,
                DNS_SOA_RECORD,
                TS_LAST_DNS_CHECK,
                TS_NEXT_DNS_CHECK,
                DNS_CHECK_PRIORITY
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """)) {

            ps.setString(1, rootDomainName());
            ps.setInt(2, nodeAffinity());

            if (aRecords() == null) {
                ps.setNull(3, java.sql.Types.VARCHAR);
            } else {
                ps.setString(3, aRecords.encode());
            }
            if (aaaaRecords() == null) {
                ps.setNull(4, java.sql.Types.VARCHAR);
            } else {
                ps.setString(4, aaaaRecords.encode());
            }
            if (cnameRecord() == null) {
                ps.setNull(5, java.sql.Types.VARCHAR);
            } else {
                ps.setString(5, cnameRecord());
            }
            if (mxRecords() == null) {
                ps.setNull(6, java.sql.Types.VARCHAR);
            } else {
                ps.setString(6, mxRecords.encode());
            }
            if (caaRecords() == null) {
                ps.setNull(7, java.sql.Types.VARCHAR);
            } else {
                ps.setString(7, caaRecords.encode());
            }
            if (txtRecords() == null) {
                ps.setNull(8, java.sql.Types.VARCHAR);
            } else {
                ps.setString(8, txtRecords().encode());
            }
            if (nsRecords() == null) {
                ps.setNull(9, java.sql.Types.VARCHAR);
            } else {
                ps.setString(9, nsRecords().encode());
            }
            if (soaRecord() == null) {
                ps.setNull(10, java.sql.Types.VARCHAR);
            } else {
                ps.setString(10, soaRecord());
            }
            ps.setString(10, soaRecord());
            ps.setTimestamp(11, java.sql.Timestamp.from(tsLastUpdate()));
            ps.setTimestamp(12, java.sql.Timestamp.from(tsNextScheduledUpdate()));
            ps.setInt(13, dnsCheckPriority());
            ps.executeUpdate();
        }
    }

    public void update(Connection connection) throws SQLException {

        try (var ps = connection.prepareStatement("""
            REPLACE INTO DOMAIN_DNS_INFORMATION (
                DNS_ROOT_DOMAIN_ID,
                ROOT_DOMAIN_NAME,
                NODE_AFFINITY,
                DNS_A_RECORDS,
                DNS_AAAA_RECORDS,
                DNS_CNAME_RECORD,
                DNS_MX_RECORDS,
                DNS_CAA_RECORDS,
                DNS_TXT_RECORDS,
                DNS_NS_RECORDS,
                DNS_SOA_RECORD,
                TS_LAST_DNS_CHECK,
                TS_NEXT_DNS_CHECK,
                DNS_CHECK_PRIORITY
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """)) {

            ps.setObject(1, dnsRootDomainId(), java.sql.Types.INTEGER);
            ps.setString(2, rootDomainName());
            ps.setInt(3, nodeAffinity());

            if (aRecords() == null) {
                ps.setNull(4, java.sql.Types.VARCHAR);
            } else {
                ps.setString(4, aRecords.encode());
            }
            if (aaaaRecords() == null) {
                ps.setNull(5, java.sql.Types.VARCHAR);
            } else {
                ps.setString(5, aaaaRecords.encode());
            }
            if (cnameRecord() == null) {
                ps.setNull(6, java.sql.Types.VARCHAR);
            } else {
                ps.setString(6, cnameRecord());
            }
            if (mxRecords() == null) {
                ps.setNull(7, java.sql.Types.VARCHAR);
            } else {
                ps.setString(7, mxRecords.encode());
            }
            if (caaRecords() == null) {
                ps.setNull(8, java.sql.Types.VARCHAR);
            } else {
                ps.setString(8, caaRecords().encode());
            }
            if (txtRecords() == null) {
                ps.setNull(9, java.sql.Types.VARCHAR);
            } else {
                ps.setString(9, txtRecords().encode());
            }
            if (nsRecords() == null) {
                ps.setNull(10, java.sql.Types.VARCHAR);
            } else {
                ps.setString(10, nsRecords().encode());
            }
            if (soaRecord() == null) {
                ps.setNull(11, java.sql.Types.VARCHAR);
            } else {
                ps.setString(11, soaRecord());
            }
            ps.setTimestamp(12, java.sql.Timestamp.from(tsLastUpdate()));
            ps.setTimestamp(13, java.sql.Timestamp.from(tsNextScheduledUpdate()));
            ps.setInt(14, dnsCheckPriority());
            ps.executeUpdate();
        }
    }

    public static class Builder {
        private Integer dnsRootDomainId;
        private String rootDomainName;
        private int nodeAffinity;
        private Set<String> aRecords;
        private Set<String> aaaaRecords;
        private String cnameRecord;
        private Set<String> mxRecords;
        private Set<String> caaRecords;
        private Set<String> txtRecords;
        private Set<String> nsRecords;
        private String soaRecord;
        private Instant tsLastUpdate;
        private Instant tsNextScheduledUpdate;
        private int dnsCheckPriority;

        public Builder dnsRootDomainId(Integer dnsRootDomainId) {
            this.dnsRootDomainId = dnsRootDomainId;
            return this;
        }

        public Builder rootDomainName(String rootDomainName) {
            this.rootDomainName = rootDomainName;
            return this;
        }

        public Builder nodeAffinity(int nodeAffinity) {
            this.nodeAffinity = nodeAffinity;
            return this;
        }

        public Builder addARecord(String aRecord) {
            if (this.aRecords == null) {
                this.aRecords = new HashSet<>();
            }
            this.aRecords.add(aRecord);
            return this;
        }

        public Builder aRecords(List<String> aRecords) {
            this.aRecords = new HashSet<>(aRecords);
            return this;
        }

        public Builder addAaaaRecord(String aaaaRecord) {
            if (this.aaaaRecords == null) {
                this.aaaaRecords = new HashSet<>();
            }
            this.aaaaRecords.add(aaaaRecord);
            return this;
        }

        public Builder aaaaRecords(List<String> aaaaRecords) {
            this.aaaaRecords = new HashSet<>(aaaaRecords);
            return this;
        }

        public Builder cnameRecord(String cnameRecord) {
            this.cnameRecord = cnameRecord;
            return this;
        }

        public Builder addMxRecord(String mxRecord) {
            if (this.mxRecords == null) {
                this.mxRecords = new HashSet<>();
            }
            this.mxRecords.add(mxRecord);
            return this;
        }

        public Builder mxRecords(List<String> mxRecords) {
            this.mxRecords = new HashSet<>(mxRecords);
            return this;
        }

        public Builder addCaaRecord(String caaRecord) {
            if (this.caaRecords == null) {
                this.caaRecords = new HashSet<>();
            }
            this.caaRecords.add(caaRecord);
            return this;
        }

        public Builder caaRecords(List<String> caaRecords) {
            this.caaRecords = new HashSet<>(caaRecords);
            return this;
        }

        public Builder addTxtRecord(String txtRecord) {
            if (this.txtRecords == null) {
                this.txtRecords = new HashSet<>();
            }
            this.txtRecords.add(txtRecord);
            return this;
        }

        public Builder txtRecords(List<String> txtRecords) {
            this.txtRecords = new HashSet<>(txtRecords);
            return this;
        }

        public Builder addNsRecord(String nsRecord) {
            if (this.nsRecords == null) {
                this.nsRecords = new HashSet<>();
            }
            this.nsRecords.add(nsRecord);
            return this;
        }

        public Builder nsRecords(List<String> nsRecords) {
            this.nsRecords = new HashSet<>(nsRecords);
            return this;
        }

        public Builder soaRecord(String soaRecord) {
            this.soaRecord = soaRecord;
            return this;
        }

        public Builder tsLastUpdate(Instant tsLastUpdate) {
            this.tsLastUpdate = tsLastUpdate;
            return this;
        }
        public Builder tsNextScheduledUpdate(Instant nextScheduledUpdate) {
            this.tsNextScheduledUpdate = nextScheduledUpdate;
            return this;
        }
        public Builder dnsCheckPriority(int dnsCheckPriority) {
            this.dnsCheckPriority = dnsCheckPriority;
            return this;
        }

        public DomainDnsRecord build() {
            return new DomainDnsRecord(
                    dnsRootDomainId,
                    rootDomainName,
                    nodeAffinity,
                    new DnsRecordsReference.ListValue(aRecords),
                    new DnsRecordsReference.ListValue(aaaaRecords),
                    StringUtils.truncate(cnameRecord, 255),
                    new DnsRecordsReference.ListValue(mxRecords),
                    new DnsRecordsReference.ListValue(caaRecords),
                    new DnsRecordsReference.ListValue(txtRecords),
                    new DnsRecordsReference.ListValue(nsRecords),
                    StringUtils.truncate(soaRecord, 255),
                    tsLastUpdate,
                    tsNextScheduledUpdate,
                    dnsCheckPriority
            );
        }

    }
}

