package nu.marginalia.assistant.eval;

import org.junit.jupiter.api.Test;

class UnitsTest {

    @Test
    void convert() {
        var units = new Units(new MathParser());
        units.convert("3.33", "cm", "m").ifPresent(System.out::println);
    }

    @Test
    void convert2() {
        var units = new Units(new MathParser());
        units.convert("10", "km", "ft").ifPresent(System.out::println);
    }

    @Test
    void convert3() {
        var units = new Units(new MathParser());
        units.convert("10", "oz", "tons").ifPresent(System.out::println);
    }

    @Test
    void convert4() {
        var units = new Units(new MathParser());
        units.convert("10", "pc", "in").ifPresent(System.out::println);
    }

    @Test
    void convert5() {
        var units = new Units(new MathParser());
        units.convert("50", "K", "K").ifPresent(System.out::println);
        units.convert("50", "F", "K").ifPresent(System.out::println);
        units.convert("50", "C", "K").ifPresent(System.out::println);
        units.convert("50", "K", "F").ifPresent(System.out::println);
        units.convert("50", "F", "F").ifPresent(System.out::println);
        units.convert("50", "C", "F").ifPresent(System.out::println);
        units.convert("50", "K", "C").ifPresent(System.out::println);
        units.convert("50", "F", "C").ifPresent(System.out::println);
        units.convert("50", "C", "C").ifPresent(System.out::println);
    }
}