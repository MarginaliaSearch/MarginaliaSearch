package nu.marginalia.nsfw.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MarginaliaApiClientTest {

    @Test
    void query() throws IOException {
        try (var client = new MarginaliaApiClient("public")) {
            System.out.println(client.query("test", 10, 1, 1));
        }

    }
}