package nu.marginalia.util.language.conf;

import lombok.AllArgsConstructor;

import java.nio.file.Path;

@AllArgsConstructor
public class LanguageModels {
    public final Path ngramBloomFilter;
    public final Path termFrequencies;

    public final Path openNLPSentenceDetectionData;
    public final Path posRules;
    public final Path posDict;
    public final Path openNLPTokenData;
}
