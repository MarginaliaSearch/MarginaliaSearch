package nu.marginalia.functions.math.eval;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;

import com.google.inject.Singleton;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Singleton
public class MathParser {
    private final NumberFormat df;
    static final Map<String, Double> constants = Map.of("e", Math.E, "pi", Math.PI, "2pi", 2*Math.PI);

    final Predicate<String> isTrivial = Pattern.compile("([0-9]+\\.[0-9]*|\\.[0-9]+)").asMatchPredicate();

    public MathParser() {
         df = DecimalFormat.getInstance(Locale.US);
         df.setRoundingMode(RoundingMode.HALF_UP);
         df.setMaximumFractionDigits(6);
    }

    public String evalFormatted(String inputExpression) {
        try {
            if (isTrivial.test(inputExpression)) {
                return df.format(Double.parseDouble(inputExpression));
            }

            return df.format(eval(inputExpression));
        }
        catch (NumberFormatException | ParseException e) {
            // We don't want to throw an exception here
            return "";
        }
    }

    @SneakyThrows
    public double eval(String inputExpression) throws ParseException {
        if (isTrivial.test(inputExpression)) {
            return Double.parseDouble(inputExpression);
        }

        List<Token> tokens = tokenize(inputExpression);

        // recursive descent

        tokens = parenthesize(tokens);
        tokens = negate(tokens);
        tokens = functions(tokens);
        tokens = binaryExpression(tokens, "^");
        tokens = binaryExpression(tokens, "*/");
        tokens = binaryExpression(tokens, "+-");

        return new GroupExpression(' ', tokens).evaluate();
    }

    List<Token> negate(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return tokens;
        }
        for (int i = 0; i < tokens.size(); i++) {
            var t = tokens.get(i);
            t.transform(this::negate);
        }


        for (int i = 0; i < tokens.size()-1;) {
            var t = tokens.get(i);

            if (t.tokenType != '-') {
                i++;
                continue;
            }

            if (i == 0) {
                tokens.set(0, new UniExpression('~', tokens.get(1)));
                tokens.remove(1);
                continue;
            }

            var t2 = tokens.get(i-1);
            if ("+-%*/A".indexOf(t2.tokenType) >= 0) {
                tokens.set(i, new UniExpression('~', tokens.get(i+1)));
                tokens.remove(i+1);
                continue;
            }

            i++;
        }
        return tokens;
    }

    List<Token> functions(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return tokens;
        }

        for (int i = 0; i < tokens.size(); i++) {
            var t = tokens.get(i);
            t.transform(this::functions);
        }


        for (int i = 0; i < tokens.size()-1;) {
            var t = tokens.get(i);

            if (t.tokenType != 'A') {
                i++;
                continue;
            }

            tokens.set(i, new BiExpression('F', tokens.get(i), tokens.get(i+1)));
            tokens.remove(i+1);
        }
        return tokens;
    }


    List<Token> binaryExpression(List<Token> tokens, String operators) {
        for (int i = 0; i < tokens.size(); i++) {
            var t = tokens.get(i);

            t.transform(toks-> binaryExpression(toks, operators));
        }

        for (int i = 1; i < tokens.size()-1; i++) {
            var t = tokens.get(i);

            if (operators.indexOf(t.tokenType) >= 0) {
                Token newToken = new BiExpression(t.tokenType, tokens.get(i-1), tokens.get(i+1));
                tokens.set(i, newToken);
                tokens.remove(i+1);
                tokens.remove(i-1);
                i = i-1;
            }

        }
        return tokens;
    }

    List<Token> parenthesize(List<Token> tokens) {
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            var t = tokens.get(i);
            if (t.tokenType == ')') {
                throw new IllegalArgumentException("Unbalanced parentheses");
            }
            if (t.tokenType == '(') {
                int j;
                for (j = i+1; j < tokens.size(); j++) {
                    var t2 = tokens.get(j);
                    if (t2.tokenType == '(') {
                        depth++;
                    }
                    else if (t2.tokenType == ')') {
                        if (depth == 0) {
                            break;
                        }
                        else {
                            depth--;
                        }
                    }
                }
                if (j == tokens.size()) {
                    throw new IllegalArgumentException("Unbalanced parentheses, depth = " + depth);
                }
                else {
                    var newToken = new GroupExpression(' ', parenthesize(new ArrayList<>(tokens.subList(i+1, j))));
                    tokens.set(i, newToken);
                    tokens.subList(i+1, j+1).clear();
                }
            }
        }
        return tokens;
    }

    List<Token> tokenize(String inputExpression) throws ParseException {
        List<Token> tokens = new ArrayList<>();

        for (int i = 0; i < inputExpression.length(); i++) {
            char c = inputExpression.charAt(i);
            if ("()+-/*^".indexOf(c) >= 0) {
                tokens.add(new Token(c));
            }
            else if (Character.isDigit(c)) {
                int j;
                boolean hasPeriod = false;
                for (j = i+1; j < inputExpression.length(); j++) {
                    char c2 = inputExpression.charAt(j);
                    if (Character.isDigit(c2)) {
                        continue;
                    }
                    if (c2 == '.') {
                        if (!hasPeriod) {
                            hasPeriod = true;
                            continue;
                        }
                        else {
                            throw new ParseException("Malformatted number in " + inputExpression, j);
                        }
                    }
                    break;
                }
                tokens.add(new StringToken('0', inputExpression.substring(i, j)));
                i = j-1;
            }
            else if (Character.isAlphabetic(c)) {
                int j;
                for (j = i+1; j < inputExpression.length(); j++) {
                    char c2 = inputExpression.charAt(j);
                    if (Character.isAlphabetic(c2)) {
                        continue;
                    }
                    break;
                }
                var str = inputExpression.substring(i, j);
                if (constants.containsKey(str)) {
                    tokens.add(new StringToken('C', str));
                }
                else {
                    tokens.add(new StringToken('A', str));
                }
                i = j-1;
            }
            else if(Character.isSpaceChar(c)) {
                //
            }
            else {
                throw new ParseException(inputExpression, i);
            }
        }
        return tokens;
    }
}

@AllArgsConstructor  @ToString
class Token {
    public final char tokenType;

    public double evaluate() {
        throw new IllegalArgumentException("Can't evaluate" + this);
    }

    public void transform(Function<List<Token>, List<Token>> mapper) {

    }
}

@ToString
class StringToken extends Token {
    public final String value;

    public StringToken(char tokenType, String value) {
        super(tokenType);

        this.value = value;
    }

    public double evaluate() {
        var cv = MathParser.constants.get(value);
        if (cv != null) {
            return cv;
        }

        return Double.parseDouble(value);
    }
}

class UniExpression extends Token {
    public final Token argument;

    public UniExpression(char tokenType, Token argument) {
        super(tokenType);

        this.argument = argument;
    }

    public String toString() {
        return String.format("(%s %s)", tokenType, argument);
    }

    @Override
    public double evaluate() {
        if (tokenType == '~') {
            return -argument.evaluate();
        }
        throw new IllegalArgumentException("Can't evaluate" + this);
    }

    public void transform(Function<List<Token>, List<Token>> mapper) {
        argument.transform(mapper);
    }
}

@ToString
class GroupExpression extends Token {
    public List<Token> argument;

    public GroupExpression(char tokenType, List<Token> argument) {
        super(tokenType);

        this.argument = argument;
    }

    @Override
    public double evaluate() {
        if (argument.size() == 1) {
            return argument.get(0).evaluate();
        }
        throw new IllegalArgumentException("Can't evaluate" + this);
    }

    public void transform(Function<List<Token>, List<Token>> mapper) {
        argument = mapper.apply(argument);
    }
}


class BiExpression extends Token {
    public final Token left;
    public final Token right;

    BiExpression(char tokenType, Token left, Token right) {
        super(tokenType);

        this.left = left;
        this.right = right;
    }

    public String toString() {
        return String.format("(%s %s %s)", tokenType, left, right);
    }

    public void transform(Function<List<Token>, List<Token>> mapper) {
        left.transform(mapper);
        right.transform(mapper);
    }

    @Override
    public double evaluate() {
        double rightVal = right.evaluate();
        switch (tokenType) {
            case '+':
                return left.evaluate() + rightVal;
            case '-':
                return left.evaluate() - rightVal;
            case '*':
                return left.evaluate() * rightVal;
            case '/': {
                if (rightVal == 0) {
                    return Double.NaN;
                }
                return left.evaluate() / rightVal;
            }
            case '%':
            {
                if (rightVal == 0) {
                    return Double.NaN;
                }
                return left.evaluate() % rightVal;
            }
            case '^':
                return Math.pow(left.evaluate(), rightVal);
            case 'F':
                return evalFunction(rightVal);
            default:
                throw new IllegalArgumentException("Can't evaluate" + this);
        }
    }

    private double evalFunction(double rightVal) {
        StringToken left2 = (StringToken) left;
        switch (left2.value.toLowerCase()) {
            case "sqrt":
                return Math.sqrt(rightVal);
            case "log":
                return Math.log(rightVal);
            case "log10":
                return Math.log10(rightVal);
            case "log2":
                return Math.log(rightVal)/Math.log(2);
            case "cos":
                return Math.cos(rightVal);
            case "sin":
                return Math.sin(rightVal);
            case "tan":
                return Math.tan(rightVal);
            default:
                throw new IllegalArgumentException("Can't evaluate" + this);
        }
    }
}