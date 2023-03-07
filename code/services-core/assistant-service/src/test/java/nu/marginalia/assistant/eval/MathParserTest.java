package nu.marginalia.assistant.eval;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

class MathParserTest {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Test
    void parse() throws ParseException {
        var parser = new MathParser();
        logger.info(parser.evalFormatted("3+5"));
        logger.info(parser.evalFormatted("1+(300+log(5))"));
        logger.info(parser.evalFormatted("sqrt(1+300)"));
        logger.info(parser.evalFormatted("sqrt(pi)"));
        logger.info(parser.evalFormatted("3+5-5"));
        logger.info(parser.evalFormatted("3+-5+5"));
        logger.info(parser.evalFormatted("3+-5+log 5"));
        logger.info(parser.evalFormatted("log -5"));
    }

    @Test
    void tokenize() throws ParseException {
        var parser = new MathParser();
        logger.info("{}", parser.tokenize("3.5"));

        logger.info("{}", parser.tokenize("(3.5 + 2)*3"));
    }

    @Test
    void parenthesize() throws ParseException {
        var parser = new MathParser();
        logger.info("{}", parser.parenthesize(parser.tokenize("3.5")));
        logger.info("{}", parser.tokenize("(3.5)"));
        logger.info("{}", parser.parenthesize(parser.tokenize("(3.5)")));
        logger.info("{}", parser.parenthesize(parser.tokenize("(3.5 * (2+5))")));
    }
}