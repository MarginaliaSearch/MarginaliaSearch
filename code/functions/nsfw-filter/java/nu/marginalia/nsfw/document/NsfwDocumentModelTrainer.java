package nu.marginalia.nsfw.document;

import nu.marginalia.classifier.BinaryClassifierTrainer;
import nu.marginalia.classifier.ClassifierVocabulary;

import java.io.IOException;
import java.nio.file.Path;

public class NsfwDocumentModelTrainer {

    public static void main(String... args) {
        Path trainingDataDir = Path.of("../../../run/training-data/nsfw/");
        Path outputDir = Path.of(args[0]);

        try {
            trainModel(
                    trainingDataDir.resolve("features.txt"),
                    trainingDataDir.resolve("samples"),
                    outputDir
            );
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    public static void trainModel(Path vocabularyFile, Path sampleDir, Path outputDir) throws IOException {

        ClassifierVocabulary vocabulary = new ClassifierVocabulary(vocabularyFile);

        var trainer = new BinaryClassifierTrainer(vocabulary,
                new String[] { "__label__SAFE", "__label__NSFW" },
                sampleDir
        );

        var model = trainer.train();

        model.save(outputDir);
        vocabulary.save(outputDir.resolve("vocabulary.txt"));
    }
}
