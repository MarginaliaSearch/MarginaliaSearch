package nu.marginalia.index.service.util;

class DictionaryHashMapTest {
//
//    @Test
//    public void testDictionaryHashMap() {
//        var dhm = new DictionaryHashMap(1<<6);
//        System.out.println(dhm.put("hello".getBytes(), 23));
//        System.out.println(dhm.put("hello".getBytes(), 23));
//        System.out.println(dhm.put("world".getBytes(), 54));
//        assertEquals(23, dhm.get("hello".getBytes()));
//        assertEquals(54, dhm.get("world".getBytes()));
//
//    }
//
//    @Test
//    public void testDictionaryHashMapMissing() {
//        var dhm = new DictionaryHashMap(1<<8);
//        assertEquals(DictionaryHashMap.NO_VALUE, dhm.get(new byte[] { 1,2,3}));
//
//    }
//
//    @Test
//    public void randomTest() {
//        Set<String> strings = new HashSet<>();
//        var dhm = new DictionaryHashMap(1<<14);
//
//        for (int i = 0; i < 10000; i++) {
//            strings.add(Double.toString(Math.random()));
//        }
//
//        for (String s : strings) {
//            dhm.put(s.getBytes(), s.hashCode());
//        }
//
//        for (String s : strings) {
//            assertEquals(s.hashCode(), dhm.get(s.getBytes()));
//        }
//
//        assertEquals(strings.size(), dhm.size());
//    }
//
//    @Test
//    public void fillHerUp2() {
//        var dhm = new DictionaryHashMap(1<<13);
//
//        try {
//            for (int i = 0; i < 10000; i++) {
//                dhm.put(Double.toString(Math.random()).getBytes(), i);
//            }
//            Assertions.fail("Expected exception");
//        }
//        catch (IllegalStateException ex) {
//            ex.printStackTrace();
//        }
//    }

}