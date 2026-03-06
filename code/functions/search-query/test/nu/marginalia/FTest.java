package nu.marginalia;

import com.github.jfasttext.JFastText;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class FTest {

    public static void exportTrainingFile(String dbPath, Path outputPathTrainingData, Path outputPathTestingData, double pTrain) throws Exception {

        Random r = new Random();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement stmt = conn.prepareStatement("""
                SELECT LABEL, TITLE, TEXT
                FROM samples
                WHERE LABEL IS NOT NULL
                AND TITLE IS NOT NULL 
                AND TEXT IS NOT NULL
                """);

             ResultSet rs = stmt.executeQuery();
             BufferedWriter trainingWriter = Files.newBufferedWriter(outputPathTrainingData);
             BufferedWriter testingWriter = Files.newBufferedWriter(outputPathTestingData);
             ) {

            while (rs.next()) {
                String label = rs.getString("LABEL");
                String title = rs.getString("TITLE");
                String text  = rs.getString("TEXT");

                String content = combine(title, text);
                if (content.isBlank()) continue;

                BufferedWriter writer = r.nextDouble() < pTrain ? trainingWriter : testingWriter;

                // FastText format: __label__X cleaned text
                writer.write("__label__" + sanitizeLabel(label) + " " + sanitizeText(content));
                writer.newLine();
            }
        }
    }

    private static String combine(String title, String text) {
        String t = title != null ? title.strip() : "";
        String b = text  != null ? text.strip()  : "";
        return (t + " " + b).strip();
    }

    /** Labels can't contain spaces */
    private static String sanitizeLabel(String label) {
        return label.strip().replace(" ", "_");
    }

    /** Remove newlines so each sample stays on one line */
    private static String sanitizeText(String text) {
        return text.replaceAll("[\\r\\n]+", " ").strip();
    }


    public static JFastText train(Path trainingFile, Path testingFile, Path modelOutput) {
        JFastText jft = new JFastText();

        jft.runCmd(new String[]{
                "supervised",
                "-input",   trainingFile.toString(),
                "-output",  modelOutput.toString(),

                // Core params — tune these for your data
                "-epoch",   "100",
                "-lr",      "0.1",
                "-dim",     "100",       // word vector size
                "-wordNgrams", "2",      // bigrams improve short-text classification
                "-minCount", "2",        // ignore rare words
                "-loss",    "softmax",   // use hs (hierarchical softmax) for many labels
                "-thread",  "4"
        });

        // Write a test file the same way as the training file, then:
        jft.runCmd(new String[]{
                "test",
                modelOutput + ".bin",
                testingFile.toString(),
                "1"   // k — top-k predictions to evaluate
        });

        return jft;
    }

    public static void growSampleDataWithMislabeledData(Path docDbPath, Path sampleDbPath,Path model) throws SQLException {

        JFastText fastText = new JFastText();
        fastText.loadModel(model.toString());

        int cnt = 5000;

        try (Connection docDbConn = DriverManager.getConnection("jdbc:sqlite:" + docDbPath);
             Connection sampleDbConn = DriverManager.getConnection("jdbc:sqlite:" + sampleDbPath);
             Statement docDbStatement = docDbConn.createStatement();
             PreparedStatement sampleDbPs = sampleDbConn.prepareStatement("""
                     INSERT INTO samples (label, title, text, url)
                     VALUES (?, ?, ?, ?)
                     """);

             ) {
            var rs = docDbStatement.executeQuery("""
                    SELECT TITLE, DESCRIPTION, URL from DOCUMENT
                    """);

            Set<String> seen = new HashSet<>();

            while (rs.next() && cnt >= 0) {
                String title = rs.getString("TITLE");
                String desc = rs.getString("DESCRIPTION");
                String url = rs.getString("URL");


                String input = sanitizeText(combine(title, desc));
                if (!seen.add(input))
                    continue;

                var labelProba = fastText.predictProba(input);
                if (labelProba != null) {
                    if ("__label__NSFW".equals(labelProba.label) && Math.exp(labelProba.logProb) > 0.75) {
                        sampleDbPs.setString(1, "unlabeled");
                        sampleDbPs.setString(2, title);
                        sampleDbPs.setString(3, desc);
                        sampleDbPs.setString(4, url);
                        sampleDbPs.execute();
                        System.out.println(labelProba.label + ":" + Math.exp(labelProba.logProb) + ": " + url + "\n" + title + " " + desc);
                        System.out.println("---");
                        cnt--;
                        for (int i = 0 ; i < 1000; i++) {
                            if (!rs.next()) break;
                        }
                    }
                }

            }
        }

    }

    @Test
    public void test() throws Exception {
//        exportTrainingFile("/home/vlofgren/Code/nsfwfilter/samples.db",
//                Path.of("/tmp/training_data.txt"),
//                Path.of("/tmp/testing_data.txt"),
//                0.8
//                );
//        train(Path.of("/tmp/training_data.txt"), Path.of("/tmp/testing_data.txt"), Path.of("/tmp/model.jft"));

        growSampleDataWithMislabeledData(Path.of("/mnt/p/storage2/tmp/documents.db"), Path.of("/home/vlofgren/Code/nsfwfilter/samples.db"), Path.of("/tmp/model.jft.bin"));

    }
}
