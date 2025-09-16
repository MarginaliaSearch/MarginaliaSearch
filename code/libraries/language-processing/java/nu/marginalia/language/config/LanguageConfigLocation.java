package nu.marginalia.language.config;

import nu.marginalia.WmsaHome;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

sealed public interface LanguageConfigLocation {
    InputStream findLanguageConfiguration(LanguageConfigLocation languageFile) throws IOException;

    final class Auto implements LanguageConfigLocation {
        @Override
        public InputStream findLanguageConfiguration(LanguageConfigLocation languageFile) throws IOException {
            Path filesystemPath = WmsaHome.getLangugeConfig();
            if (Files.exists(filesystemPath)) {
                return Files.newInputStream(filesystemPath, StandardOpenOption.READ);
            }
            if (Boolean.getBoolean("language.experimental")) {
                return ClassLoader.getSystemResourceAsStream("languages-experimental.xml");
            } else {
                return ClassLoader.getSystemResourceAsStream("languages-default.xml");
            }
        }
    }

    final class Experimental implements LanguageConfigLocation {
        @Override
        public InputStream findLanguageConfiguration(LanguageConfigLocation languageFile) throws IOException {
            return ClassLoader.getSystemResourceAsStream("languages-experimental.xml");
        }
    }

    final class Default implements LanguageConfigLocation {

        @Override
        public InputStream findLanguageConfiguration(LanguageConfigLocation languageFile) throws IOException {
            return ClassLoader.getSystemResourceAsStream("languages-default.xml");
        }
    }
}
