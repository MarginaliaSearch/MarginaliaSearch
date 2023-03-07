package nu.marginalia.client;

public final class HttpStatusCode {
    public final int code;

    public HttpStatusCode(int code) {
        this.code = code;
    }

    public boolean isGood() {
        if (code == org.apache.http.HttpStatus.SC_OK)
            return true;
        if (code == org.apache.http.HttpStatus.SC_ACCEPTED)
            return true;
        if (code == org.apache.http.HttpStatus.SC_CREATED)
            return true;
        return false;
    }
}
