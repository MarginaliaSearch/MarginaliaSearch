package nu.marginalia;

import java.nio.file.Path;

public class LanguageModels {
    public final Path ngramBloomFilter;
    public final Path termFrequencies;

    public final Path openNLPSentenceDetectionData;
    public final Path posRules;
    public final Path posDict;
    public final Path openNLPTokenData;
    public final Path fasttextLanguageModel;

    public LanguageModels(Path ngramBloomFilter,
                          Path termFrequencies,
                          Path openNLPSentenceDetectionData,
                          Path posRules,
                          Path posDict,
                          Path openNLPTokenData,
                          Path fasttextLanguageModel) {
        this.ngramBloomFilter = ngramBloomFilter;
        this.termFrequencies = termFrequencies;
        this.openNLPSentenceDetectionData = openNLPSentenceDetectionData;
        this.posRules = posRules;
        this.posDict = posDict;
        this.openNLPTokenData = openNLPTokenData;
        this.fasttextLanguageModel = fasttextLanguageModel;
    }
}
