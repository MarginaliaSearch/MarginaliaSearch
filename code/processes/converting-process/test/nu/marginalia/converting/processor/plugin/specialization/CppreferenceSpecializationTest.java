package nu.marginalia.converting.processor.plugin.specialization;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class CppreferenceSpecializationTest {
    CppreferenceSpecialization specialization = new CppreferenceSpecialization(null, null);

    @Test
    public void testTitleMagic() {

        List<String> ret;

        ret = specialization.extractExtraTokens("std::multimap<Key, T, Compare, Allocator>::crend - cppreference.com");
        Assertions.assertTrue(ret.contains("std::multimap::crend"));
        Assertions.assertTrue(ret.contains("multimap::crend"));
        Assertions.assertTrue(ret.contains("std::multimap"));
        Assertions.assertTrue(ret.contains("crend"));

        ret = specialization.extractExtraTokens("std::coroutine_handle<Promise>::operator(), std::coroutine_handle<Promise>::resume - cppreference.com");
        Assertions.assertTrue(ret.contains("std::coroutine_handle::operator()"));
        Assertions.assertTrue(ret.contains("std::coroutine_handle::resume"));
    }

}