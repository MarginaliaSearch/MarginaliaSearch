package nu.marginalia.wmsa.smhi.scraper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.opencsv.CSVReader;
import nu.marginalia.wmsa.smhi.model.Plats;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Singleton
public class PlatsReader {
    private final String fileName;

    @Inject
    public PlatsReader(@Named("plats-csv-file") String fileName) {
        this.fileName = fileName;
    }

    public List<Plats> readPlatser() throws Exception {
        List<Plats> platser = new ArrayList<>();

        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(fileName),
                "Kunde inte ladda " + fileName);
        try (var reader = new CSVReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            for (;;) {
                String[] strings = reader.readNext();
                if (strings == null) {
                    return platser;
                }
                platser.add(skapaPlats(strings));
            }
        }

    }

    private Plats skapaPlats(String[] strings) {
        return new Plats(strings[0], strings[1], strings[2]);
    }
}
