package nu.marginalia.assistant.dict;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.assistant.client.model.DictionaryEntry;
import nu.marginalia.assistant.client.model.DictionaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Singleton
public class DictionaryService {

    private final HikariDataSource dataSource;
    private final SpellChecker spellChecker;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public DictionaryService(HikariDataSource dataSource, SpellChecker spellChecker)
    {
        this.spellChecker = spellChecker;
        this.dataSource = dataSource;
    }

    public DictionaryResponse define(String word) {
        DictionaryResponse response = new DictionaryResponse();
        response.entries = new ArrayList<>();

        try (var connection = dataSource.getConnection()) {
            var stmt = connection.prepareStatement("SELECT TYPE,WORD,DEFINITION FROM REF_DICTIONARY WHERE WORD=?");
            stmt.setString(1, word);

            var rsp = stmt.executeQuery();
            while (rsp.next()) {
                response.entries.add(new DictionaryEntry(rsp.getString(1), rsp.getString(2), rsp.getString(3)));
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return response;
    }

    public List<String> spellCheck(String word) {
        return spellChecker.correct(word);
    }
}
