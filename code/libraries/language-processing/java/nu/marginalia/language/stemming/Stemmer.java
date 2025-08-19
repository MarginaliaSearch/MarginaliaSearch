package nu.marginalia.language.stemming;

import opennlp.tools.stemmer.snowball.SnowballStemmer;

public sealed interface Stemmer {
    String stem(String input);

    final class Porter implements Stemmer {
        private static final ca.rmen.porterstemmer.PorterStemmer porterStemmerImpl = new  ca.rmen.porterstemmer.PorterStemmer();
        @Override
        public String stem(String input) {
            return porterStemmerImpl.stemWord(input);
        }
    }

    final class Snowball implements Stemmer {
        private final SnowballStemmer snowballStemmer;

        public Snowball(String algorithmName) {
            SnowballStemmer.ALGORITHM algorithm = SnowballStemmer.ALGORITHM.valueOf(algorithmName.toUpperCase());
            snowballStemmer = new SnowballStemmer(algorithm);
        }

        @Override
        public String stem(String input) {
            // Snowball impl declares return value as CharSequence,
            // but in practice always returns a String
            return (String) snowballStemmer.stem(input);
        }
    }

    final class NoOpStemmer implements Stemmer {

        @Override
        public String stem(String input) {
            return input;
        }
    }
}
