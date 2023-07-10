package nu.marginalia.converting.processor.plugin.specialization;

import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlogSpecializationTest {

    @Test
    void shouldIndex() throws Exception {
        var spec = new BlogSpecialization(null);
        assertFalse(spec.shouldIndex(new EdgeUrl("https://blog.marginalia.nu/2023/00/22/")));
        assertFalse(spec.shouldIndex(new EdgeUrl("https://blog.marginalia.nu/2023/00/")));
        assertFalse(spec.shouldIndex(new EdgeUrl("https://blog.marginalia.nu/00/22/")));
    }
}