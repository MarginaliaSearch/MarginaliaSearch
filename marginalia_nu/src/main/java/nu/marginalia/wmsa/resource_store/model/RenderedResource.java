package nu.marginalia.wmsa.resource_store.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
public class RenderedResource {
    public final String filename;
    public final String data;
    public final String genTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    public final long genTimeMillis = System.currentTimeMillis();
    public final String expiry;
    public final boolean requireLogin;

    public RenderedResource(String filename, LocalDateTime expiryDate, String data) {
        this.filename = filename;
        this.data = data;
        this.expiry = expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.requireLogin = false;
    }
    public RenderedResource(String filename, LocalDateTime expiryDate, String data, boolean requireLogin) {
        this.filename = filename;
        this.data = data;
        this.expiry = expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.requireLogin = requireLogin;
    }
    public boolean isExpired() {
        var expiryDate = LocalDateTime.parse(expiry, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return expiryDate.isBefore(LocalDateTime.now());

    }

    public String etag() {
        return "\"" + genTime.hashCode() + "-" + data.hashCode() + "\"";
    }

    public String diskFileName() {
        return filename.hashCode() + "-" + data.hashCode() + ".html";
    }

    public long size() {
        return 2L*(data.length()+filename.length());
    }
}
