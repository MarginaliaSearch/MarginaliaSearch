package nu.marginalia.functions.math.eval;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.opencsv.CSVReader;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class Units {

    private final Map<String, Unit> unitsByName = new HashMap<>();
    private final MathParser mathParser;

    @Inject
    public Units(MathParser mathParser)  {
        this.mathParser = mathParser;

        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("units.csv"),
                "Could not load IP location db");

        try (var reader = new CSVReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            for (;;) {
                String[] vals = reader.readNext();
                if (vals == null) {
                    break;
                }

                var unit = new Unit(vals[1], Double.parseDouble(vals[0]), vals[2]);

                for (int i = 2; i < vals.length; i++) {
                    unitsByName.put(vals[i].toLowerCase(), unit);
                }
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    public Optional<String> convert(String value, String fromUnitName, String toUnitName) {
        var fromUnit = unitsByName.get(fromUnitName.toLowerCase());
        var toUnit = unitsByName.get(toUnitName.toLowerCase());

        if (Objects.equals(fromUnit, toUnit)) {
            return Optional.of(value + " " + fromUnit.name);
        }
        if (null == fromUnit || null == toUnit) {
            return Optional.empty();
        }

        if (!Objects.equals(toUnit.type, fromUnit.type)) {
            return Optional.empty();
        }

        double valNum;
        try {
            valNum = mathParser.eval(value);
        }
        catch (Exception ex) {
            return Optional.empty();
        }
        double convertedValue;
        if ("TEMPERATURE".equals(fromUnit.type)) {
            convertedValue = convertTemperature(valNum, fromUnit, toUnit);
        }
        else {
            convertedValue = fromUnit.baseValue * valNum / toUnit.baseValue;
        }

        boolean negative = convertedValue < 0;
        if (negative) {
            convertedValue = -convertedValue;
        }

        long intFraction = (int) Math.log10(convertedValue);

        int sigFigs = countSigFigs(value);
        var nf = new DecimalFormat();
        nf.setMaximumIntegerDigits(1 + (int) intFraction);
        nf.setMaximumFractionDigits(1 + sigFigs - (int)intFraction);
        return Optional.of((negative ? "-":"") + nf.format(convertedValue) + " " + toUnit.name);
    }

    private double convertTemperature(double valNum, Unit fromUnit, Unit toUnit) {
        if ("C".equals(fromUnit.name)) {
            if ("K".equals(toUnit.name)) {
                return valNum + 273.15;
            }
            else if ("F".equals(toUnit.name)) {
                return 32. + 9*valNum/5;
            }
        }
        else if ("F".equals(fromUnit.name)) {
            if ("C".equals(toUnit.name)) {
                return 5*(valNum - 32.)/9;
            }
            if ("K".equals(toUnit.name)) {
                return 5*(valNum - 32.)/9 + 273.15;
            }
        }
        else if ("K".equals(fromUnit.name)) {
            if ("C".equals(toUnit.name)) {
                return valNum - 273.15;
            }
            else if ("F".equals(toUnit.name)) {
                return 32. + 9*(valNum-273.15)/5;
            }
        }
        return 0;
    }

    private int countSigFigs(String value) {
        return (int) value.chars().filter(Character::isDigit).count();
    }
}
