package nu.marginalia.language.stemming;

import nu.marginalia.language.pos.PosPattern;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

import javax.annotation.Nullable;

public sealed interface Stemmer {
    String stem(String input);
    @Nullable PosPattern inclusionPatten();

    final class Porter implements Stemmer {
        private static final ca.rmen.porterstemmer.PorterStemmer porterStemmerImpl = new  ca.rmen.porterstemmer.PorterStemmer();
        @Nullable
        private final PosPattern inclusionPattern;

        public Porter(@Nullable PosPattern inclusionPattern) {
            this.inclusionPattern = inclusionPattern;
        }

        @Nullable
        public PosPattern inclusionPatten() {
            return inclusionPattern;
        }
        @Override
        public String stem(String input) {
            return porterStemmerImpl.stemWord(input);
        }
    }

    final class Snowball implements Stemmer {
        private final SnowballStemmer snowballStemmer;
        @Nullable
        private final PosPattern inclusionPattern;

        public Snowball(String algorithmName, @Nullable PosPattern inclusionPattern) {
            this.inclusionPattern = inclusionPattern;

            SnowballStemmer.ALGORITHM algorithm = SnowballStemmer.ALGORITHM.valueOf(algorithmName.toUpperCase());
            snowballStemmer = new SnowballStemmer(algorithm);
        }

        @Nullable
        public PosPattern inclusionPatten() {
            return inclusionPattern;
        }

        @Override
        public String stem(String input) {
            // Snowball impl declares return value as CharSequence,
            // but in practice always returns a String
            return (String) snowballStemmer.stem(input);
        }
    }

    final class NoOpStemmer implements Stemmer {

        @Nullable
        public PosPattern inclusionPatten() {
            return null;
        }

        @Override
        public String stem(String input) {
            return input;
        }
    }
}
