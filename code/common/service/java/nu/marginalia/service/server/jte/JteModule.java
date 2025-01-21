package nu.marginalia.service.server.jte;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import io.jooby.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

// Temporary workaround for a bug
// APL-2.0 https://github.com/jooby-project/jooby
public class JteModule implements Extension {
    private Path sourceDirectory;
    private Path classDirectory;
    private TemplateEngine templateEngine;

    public JteModule(@NonNull Path sourceDirectory, @NonNull Path classDirectory) {
        this.sourceDirectory = (Path)Objects.requireNonNull(sourceDirectory, "Source directory is required.");
        this.classDirectory = (Path)Objects.requireNonNull(classDirectory, "Class directory is required.");
    }

    public JteModule(@NonNull Path sourceDirectory) {
        this.sourceDirectory = (Path)Objects.requireNonNull(sourceDirectory, "Source directory is required.");
    }

    public JteModule(@NonNull TemplateEngine templateEngine) {
        this.templateEngine = (TemplateEngine)Objects.requireNonNull(templateEngine, "Template engine is required.");
    }

    public void install(@NonNull Jooby application) {
        if (this.templateEngine == null) {
            this.templateEngine = create(application.getEnvironment(), this.sourceDirectory, this.classDirectory);
        }

        ServiceRegistry services = application.getServices();
        services.put(TemplateEngine.class, this.templateEngine);
        application.encoder(MediaType.html, new JteTemplateEngine(this.templateEngine));
    }

    public static TemplateEngine create(@NonNull Environment environment, @NonNull Path sourceDirectory, @Nullable Path classDirectory) {
        boolean dev = environment.isActive("dev", new String[]{"test"});
        if (dev) {
            Objects.requireNonNull(sourceDirectory, "Source directory is required.");
            Path requiredClassDirectory = (Path)Optional.ofNullable(classDirectory).orElseGet(() -> sourceDirectory.resolve("jte-classes"));
            TemplateEngine engine = TemplateEngine.create(new DirectoryCodeResolver(sourceDirectory), requiredClassDirectory, ContentType.Html, environment.getClassLoader());
            Optional<List<String>> var10000 = Optional.ofNullable(System.getProperty("jooby.run.classpath")).map((it) -> it.split(File.pathSeparator)).map(Stream::of).map(Stream::toList);
            Objects.requireNonNull(engine);
            var10000.ifPresent(engine::setClassPath);
            return engine;
        } else {
            return classDirectory == null ? TemplateEngine.createPrecompiled(ContentType.Html) : TemplateEngine.createPrecompiled(classDirectory, ContentType.Html);
        }
    }
}
