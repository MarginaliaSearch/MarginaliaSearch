package nu.marginalia.wmsa.edge.crawler.domain.language.conf;

import lombok.AllArgsConstructor;

import java.nio.file.Path;

@AllArgsConstructor
public class LanguageModels {
    public final Path ngramDictionary;
    public final Path ngramFrequency;
    public final Path openNLPSentenceDetectionData;
    public final Path posRules;
    public final Path posDict;
    public final Path openNLPTokenData;
}
