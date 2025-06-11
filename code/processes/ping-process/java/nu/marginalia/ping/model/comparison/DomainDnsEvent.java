package nu.marginalia.ping.model.comparison;

import nu.marginalia.ping.model.DomainDnsRecord;
import nu.marginalia.ping.model.WritableModel;
import nu.marginalia.ping.util.JsonObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

public record DomainDnsEvent(
        int rootDomainId,
        int nodeId,

        Instant tsChange,
        boolean changeARecords,
        boolean changeAaaaRecords,
        boolean changeCname,
        boolean changeMxRecords,
        boolean changeCaaRecords,
        boolean changeTxtRecords,
        boolean changeNsRecords,
        boolean changeSoaRecord,

        JsonObject<DomainDnsRecord> dnsSignatureBefore,
        JsonObject<DomainDnsRecord> dnsSignatureAfter
) implements WritableModel {

    @Override
    public void write(Connection connection) throws SQLException {
        try (var ps = connection.prepareStatement("""
                INSERT INTO DOMAIN_DNS_EVENTS (
                    DNS_ROOT_DOMAIN_ID,
                    NODE_ID,
                    TS_CHANGE,
                    CHANGE_A_RECORDS,
                    CHANGE_AAAA_RECORDS,
                    CHANGE_CNAME,
                    CHANGE_MX_RECORDS,
                    CHANGE_CAA_RECORDS,
                    CHANGE_TXT_RECORDS,
                    CHANGE_NS_RECORDS,
                    CHANGE_SOA_RECORD,
                    DNS_SIGNATURE_BEFORE,
                    DNS_SIGNATURE_AFTER
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """)) {
            ps.setInt(1, rootDomainId());
            ps.setInt(2, nodeId());
            ps.setTimestamp(3, java.sql.Timestamp.from(tsChange()));
            ps.setBoolean(4, changeARecords());
            ps.setBoolean(5, changeAaaaRecords());
            ps.setBoolean(6, changeCname());
            ps.setBoolean(7, changeMxRecords());
            ps.setBoolean(8, changeCaaRecords());
            ps.setBoolean(9, changeTxtRecords());
            ps.setBoolean(10, changeNsRecords());
            ps.setBoolean(11, changeSoaRecord());
            if (dnsSignatureBefore() == null) {
                ps.setNull(12, Types.BLOB);
            } else {
                ps.setBytes(12, dnsSignatureBefore().compressed());
            }
            if (dnsSignatureAfter() == null) {
                ps.setNull(13, Types.BLOB);
            } else {
                ps.setBytes(13, dnsSignatureAfter().compressed());
            }
            ps.executeUpdate();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int rootDomainId;
        private int nodeId;
        private Instant tsChange;
        private boolean changeARecords;
        private boolean changeAaaaRecords;
        private boolean changeCname;
        private boolean changeMxRecords;
        private boolean changeCaaRecords;
        private boolean changeTxtRecords;
        private boolean changeNsRecords;
        private boolean changeSoaRecord;
        private JsonObject<DomainDnsRecord> dnsSignatureBefore;
        private JsonObject<DomainDnsRecord> dnsSignatureAfter;

        public Builder rootDomainId(int rootDomainId) {
            this.rootDomainId = rootDomainId;
            return this;
        }

        public Builder nodeId(int nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder tsChange(Instant tsChange) {
            this.tsChange = tsChange;
            return this;
        }

        public Builder changeARecords(boolean changeARecords) {
            this.changeARecords = changeARecords;
            return this;
        }

        public Builder changeAaaaRecords(boolean changeAaaaRecords) {
            this.changeAaaaRecords = changeAaaaRecords;
            return this;
        }

        public Builder changeCname(boolean changeCname) {
            this.changeCname = changeCname;
            return this;
        }

        public Builder changeMxRecords(boolean changeMxRecords) {
            this.changeMxRecords = changeMxRecords;
            return this;
        }

        public Builder changeCaaRecords(boolean changeCaaRecords) {
            this.changeCaaRecords = changeCaaRecords;
            return this;
        }

        public Builder changeTxtRecords(boolean changeTxtRecords) {
            this.changeTxtRecords = changeTxtRecords;
            return this;
        }

        public Builder changeNsRecords(boolean changeNsRecords) {
            this.changeNsRecords = changeNsRecords;
            return this;
        }

        public Builder changeSoaRecord(boolean changeSoaRecord) {
            this.changeSoaRecord = changeSoaRecord;
            return this;
        }

        public Builder dnsSignatureBefore(JsonObject<DomainDnsRecord> dnsSignatureBefore) {
            this.dnsSignatureBefore = dnsSignatureBefore;
            return this;
        }

        public Builder dnsSignatureAfter(JsonObject<DomainDnsRecord> dnsSignatureAfter) {
            this.dnsSignatureAfter = dnsSignatureAfter;
            return this;
        }

        public DomainDnsEvent build() {
            return new DomainDnsEvent(
                    rootDomainId,
                    nodeId,
                    tsChange,
                    changeARecords,
                    changeAaaaRecords,
                    changeCname,
                    changeMxRecords,
                    changeCaaRecords,
                    changeTxtRecords,
                    changeNsRecords,
                    changeSoaRecord,
                    dnsSignatureBefore,
                    dnsSignatureAfter
            );
        }
    }
}
