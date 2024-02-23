package nu.marginalia.test;

import java.nio.charset.StandardCharsets;

public class CommonTestData {
    public static String loadTestData(String path) {
        try (var resourceStream = CommonTestData.class.getClassLoader().getResourceAsStream(path)) {
            if (resourceStream == null) throw new IllegalArgumentException("No such resource: " + path);

            return new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
