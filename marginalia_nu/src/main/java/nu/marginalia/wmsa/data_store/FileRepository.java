package nu.marginalia.wmsa.data_store;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.ZipFile;


public class FileRepository {
    @Inject @Named("file-storage-dir")
    private String fileStoreDir;

    @Inject @Named("file-tmp-dir")
    private String fileTempDir;

    @Inject @Named("distro-file-name")
    private String distroFileName;

    private String version;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    final ReadWriteLock rwl = new ReentrantReadWriteLock();

    @SneakyThrows
    public Object uploadFile(Request request, Response response) {

        request.attribute("org.eclipse.jetty.multipartConfig",
                new MultipartConfigElement(fileTempDir, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
        );

        final var part = Objects.requireNonNull(request.raw().getPart("uploaded_file"), "Missing part");

        var lock = rwl.writeLock();
        try (InputStream is = part.getInputStream()) {
            lock.lock();

            var tempPath
                    = Files.createTempFile(Path.of(fileStoreDir),
                    "upload-", ".jar");

            var tempFile = tempPath.toFile();

            try (var os = new FileOutputStream(tempFile)) {
                is.transferTo(os);
            }

            var oldVersion = Optional.ofNullable(version)
                    .orElseGet(() -> readJarVersion(getReleasePath().toFile()));
            var newVersion = readJarVersion(tempFile);

            logger.info("Uploading new version {}, replacing {}", newVersion, oldVersion);

            Files.move(tempPath, Path.of(fileStoreDir).resolve(distroFileName),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

            version = newVersion;
        }
        finally {
            lock.unlock();
        }

        response.status(HttpStatus.ACCEPTED_202);
        return "";
    }

    @SneakyThrows
    private String readJarVersion(File tempFile) {
        try (var zipFile = new ZipFile(tempFile)) {
            return new String(zipFile.getInputStream(zipFile.getEntry("_version.txt")).readAllBytes());
        }
    }

    @SneakyThrows
    public Object downloadFile(Request request, Response response) {
        response.type("application/java-archive");
        response.header("Content-Disposition", "attachment; filename=" + distroFileName);

        Lock lock = rwl.readLock();
        try (var is = new FileInputStream(getReleasePath().toFile())) {
            lock.lock();
            is.transferTo(response.raw().getOutputStream());
        }
        finally {
            lock.unlock();
        }
        return "";
    }

    private Path getReleasePath() {
        return Path.of(fileStoreDir, distroFileName);
    }

    @SneakyThrows
    public synchronized Object version(Request request, Response response) {
        Lock lock = rwl.readLock();
        try {
            lock.lock();

            if (null != version) {
                return version;
            }

            return readJarVersion(getReleasePath().toFile());
        }
        finally {
            lock.unlock();
        }
    }

    public Object uploadForm(Request request, Response response) {
        return "<form action='/release' method='post' enctype='multipart/form-data'>" // note the enctype
                + "    <input type='file' name='uploaded_file' accept='.jar'>"
                + "    <input type='submit'>"
                + "</form>";
    }

}
