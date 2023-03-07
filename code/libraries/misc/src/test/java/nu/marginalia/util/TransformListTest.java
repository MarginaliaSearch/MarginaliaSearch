package nu.marginalia.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformListTest {

    @Test
    void transformEach() {

        List<Integer> values = Stream.of(1,2,3,4).collect(Collectors.toList());
        new TransformList<>(values).transformEach(e -> {
            int v = e.value();
            if (v == 1) e.remove();
            if (v == 2) e.replace(5);
            if (v == 4) e.remove();
        });

        assertEquals(List.of(5,3), values);
    }

    @Test
    void transformEachPairRemoveReplace() {
        List<Integer> values = Stream.of(1,2,3,4,5,6).collect(Collectors.toList());
        new TransformList<>(values).transformEachPair((a,b) -> {
            System.out.println(a.value() + ":" + b.value());
            int v = a.value();
            if (v == 1 || v == 3 || v == 5) {
                a.remove();
                b.replace(-b.value());
            }

        });

        assertEquals(List.of(-2, -4, -6), values);
    }

    @Test
    void transformEachPairRemoveRemove() {
        List<Integer> values = Stream.of(1,2,3,4,5,6).collect(Collectors.toList());
        new TransformList<>(values).transformEachPair((a,b) -> {
            System.out.println(a.value() + ":" + b.value());
            int v = a.value();
            if (v == 1 || v == 3 || v == 5) {
                a.remove();
                b.remove();
            }

        });

        assertEquals(List.of(), values);
    }

    @Test
    void transformEachPairReplaceRemove() {
        List<Integer> values = Stream.of(1,2,3,4,5,6).collect(Collectors.toList());
        new TransformList<>(values).transformEachPair((a,b) -> {
            System.out.println(a.value() + ":" + b.value());
            int v = a.value();
            if (v == 1 || v == 3 || v == 5) {
                a.replace(-a.value());
                b.remove();
            }

        });

        assertEquals(List.of(-1, -3, -5), values);
    }

    @Test
    void transformEachPairReplaceReplace() {
        List<Integer> values = Stream.of(1,2,3,4,5,6).collect(Collectors.toList());
        new TransformList<>(values).transformEachPair((a,b) -> {
            System.out.println(a.value() + ":" + b.value());
            int v = a.value();
            if (v == 1 || v == 3 || v == 5) {
                a.replace(-a.value());
                b.replace(-b.value());
            }

        });

        assertEquals(List.of(-1, -2, -3, -4, -5, -6), values);
    }

    @Test
    void scanAndTransform() {
        List<Integer> values = Stream.of(1,2,3,4,5,6,7,8,9,10).collect(Collectors.toList());
        new TransformList<>(values).scanAndTransform(Integer.valueOf(3)::equals, Integer.valueOf(7)::equals, entity -> {
            entity.replace(entity.value() * 2);
        });

        assertEquals(List.of(1,2,6,8,10,12,14,8,9,10), values);
    }

    @Test
    void scanAndTransformEndsAtEnd() {
        List<Integer> values = Stream.of(1,2,3,4,5,6,7,8,9,10).collect(Collectors.toList());
        new TransformList<>(values).scanAndTransform(Integer.valueOf(3)::equals, Integer.valueOf(10)::equals, entity -> {
            entity.replace(entity.value() * 2);
        });

        assertEquals(List.of(1,2,6,8,10,12,14,16,18,20), values);
    }

    @Test
    void scanAndTransformOverlap() {
        List<Integer> values = Stream.of(1,2,3,3,5,7,7,8,9,10).collect(Collectors.toList());
        new TransformList<>(values).scanAndTransform(Integer.valueOf(3)::equals, Integer.valueOf(7)::equals, entity -> {
            entity.replace(entity.value() * 2);
        });

        assertEquals(List.of(1, 2, 6, 6, 10, 14, 7, 8, 9, 10), values);
    }
}