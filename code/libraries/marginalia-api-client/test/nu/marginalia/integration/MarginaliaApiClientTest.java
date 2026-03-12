package nu.marginalia.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@Tag("flakey")
class MarginaliaApiClientTest {

    @Test
    void query() throws IOException {
        try (var client = new MarginaliaApiClient("public")) {
            System.out.println(client.query(
                    ctx -> {
                        ctx.query("test");
                        ctx.count(100);
                        ctx.nsfw(2);
                        ctx.page(3);
                    }
            ));
        }

    }
}