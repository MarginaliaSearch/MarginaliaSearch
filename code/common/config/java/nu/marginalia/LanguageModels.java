package nu.marginalia;

import java.nio.file.Path;

public class LanguageModels {
    public final Path termFrequencies;

    public final Path openNLPSentenceDetectionData;
    public final Path posRules;
    public final Path posDict;
    public final Path fasttextLanguageModel;
    public final Path segments;

    public LanguageModels(Path termFrequencies,
                          Path openNLPSentenceDetectionData,
                          Path posRules,
                          Path posDict,
                          Path fasttextLanguageModel,
                          Path segments) {
        this.termFrequencies = termFrequencies;
        this.openNLPSentenceDetectionData = openNLPSentenceDetectionData;
        this.posRules = posRules;
        this.posDict = posDict;
        this.fasttextLanguageModel = fasttextLanguageModel;
        this.segments = segments;
    }

    public static LanguageModelsBuilder builder() {
        return new LanguageModelsBuilder();
    }

    public static class LanguageModelsBuilder {
        private Path termFrequencies;
        private Path openNLPSentenceDetectionData;
        private Path posRules;
        private Path posDict;
        private Path fasttextLanguageModel;
        private Path segments;

        LanguageModelsBuilder() {
        }

        public LanguageModelsBuilder termFrequencies(Path termFrequencies) {
            this.termFrequencies = termFrequencies;
            return this;
        }

        public LanguageModelsBuilder openNLPSentenceDetectionData(Path openNLPSentenceDetectionData) {
            this.openNLPSentenceDetectionData = openNLPSentenceDetectionData;
            return this;
        }

        public LanguageModelsBuilder posRules(Path posRules) {
            this.posRules = posRules;
            return this;
        }

        public LanguageModelsBuilder posDict(Path posDict) {
            this.posDict = posDict;
            return this;
        }

        public LanguageModelsBuilder fasttextLanguageModel(Path fasttextLanguageModel) {
            this.fasttextLanguageModel = fasttextLanguageModel;
            return this;
        }

        public LanguageModelsBuilder segments(Path segments) {
            this.segments = segments;
            return this;
        }

        public LanguageModels build() {
            return new LanguageModels(this.termFrequencies, this.openNLPSentenceDetectionData, this.posRules, this.posDict, this.fasttextLanguageModel, this.segments);
        }

        public String toString() {
            return "LanguageModels.LanguageModelsBuilder(termFrequencies=" + this.termFrequencies + ", openNLPSentenceDetectionData=" + this.openNLPSentenceDetectionData + ", posRules=" + this.posRules + ", posDict=" + this.posDict + ", fasttextLanguageModel=" + this.fasttextLanguageModel + ", segments=" + this.segments + ")";
        }
    }
}
