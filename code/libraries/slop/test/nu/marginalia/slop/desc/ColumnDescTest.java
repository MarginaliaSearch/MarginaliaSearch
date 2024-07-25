package nu.marginalia.slop.desc;

import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnDescTest {
    @Test
    void testParse() {
        ColumnDesc name = ColumnDesc.parse("foo.0.dat.s32le.bin");
        assertEquals("foo.0.dat.s32le.bin", name.toString());
        assertEquals("foo", name.name());
        assertEquals(0, name.page());
        assertEquals(ByteOrder.LITTLE_ENDIAN, name.byteOrder());
        assertEquals(ColumnFunction.DATA, name.function());
        assertEquals(ColumnType.INT_LE, name.type());
        assertEquals(StorageType.PLAIN, name.storageType());

        name = ColumnDesc.parse("bar.1.dat-len.fp32be.gz");
        assertEquals("bar.1.dat-len.fp32be.gz", name.toString());
        assertEquals("bar", name.name());
        assertEquals(1, name.page());
        assertEquals(ByteOrder.BIG_ENDIAN, name.byteOrder());
        assertEquals(ColumnFunction.DATA_LEN, name.function());
        assertEquals(ColumnType.FLOAT_BE, name.type());
        assertEquals(StorageType.GZIP, name.storageType());


    }
}