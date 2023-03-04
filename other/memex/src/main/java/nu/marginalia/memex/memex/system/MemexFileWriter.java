package nu.marginalia.memex.memex.system;

import nu.marginalia.util.FileSizeUtil;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class MemexFileWriter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Path renderedResourcesRoot;


    private final Set<PosixFilePermission> filePermission = Set.of(PosixFilePermission.OWNER_READ,
                                                    PosixFilePermission.OWNER_WRITE,
                                                    PosixFilePermission.GROUP_READ,
                                                    PosixFilePermission.OTHERS_READ);

    private final Set<PosixFilePermission> dirPermission = Set.of(PosixFilePermission.OWNER_READ,
                                                    PosixFilePermission.OWNER_WRITE,
                                                    PosixFilePermission.OWNER_EXECUTE,
                                                    PosixFilePermission.GROUP_READ,
                                                    PosixFilePermission.GROUP_EXECUTE,
                                                    PosixFilePermission.OTHERS_READ,
                                                    PosixFilePermission.OTHERS_EXECUTE);

    public MemexFileWriter(Path renderedResourcesRoot) {
        this.renderedResourcesRoot = renderedResourcesRoot;
    }

    public boolean exists(MemexNodeUrl url) {
        return Files.exists(getPath(url));
    }

    public void write(MemexNodeUrl url, String contents) throws IOException {
        logger.info("write({},{})", url, FileSizeUtil.readableSize(contents.length()));
        var destPath = getPath(url);
        var tempFile = Files.createTempFile(renderedResourcesRoot, url.getFilename(), ".tmp");
        ensureDirectoryExists(destPath.getParent());

        Files.createDirectories(destPath.getParent());
        Files.writeString(tempFile, contents);
        Files.move(tempFile, destPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        setFilePermissions(destPath);
    }

    private void ensureDirectoryExists(Path dir) throws IOException {
        Files.createDirectories(dir);
        setDirPermissions(dir);
    }

    private void setDirPermissions(Path dir) throws IOException {
        for (var rel = renderedResourcesRoot.relativize(dir); rel != null; rel = rel.getParent()) {
            Files.setPosixFilePermissions(renderedResourcesRoot.resolve(rel), dirPermission);
        }
    }

    public void write(MemexNodeUrl url, byte[] contents) throws IOException {
        logger.info("write({}, {})", url, FileSizeUtil.readableSize(contents.length));

        var destPath = getPath(url);
        var tempFile = Files.createTempFile(renderedResourcesRoot, url.getFilename(), ".tmp");
        ensureDirectoryExists(destPath.getParent());
        Files.write(tempFile, contents);
        Files.move(tempFile, destPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        setFilePermissions(destPath);
    }

    public void write(MemexNodeUrl url, Path realPath) throws IOException {
        logger.info("copy({} from {})", url, realPath);

        var destPath = getPath(url);
        var tempFile = Files.createTempFile(renderedResourcesRoot, url.getFilename(), ".tmp");
        ensureDirectoryExists(destPath.getParent());
        Files.copy(realPath, tempFile, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tempFile, destPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        setFilePermissions(destPath);
    }

    public void write(MemexNodeUrl url, WriteOperation wo) throws IOException {
        logger.info("write({}, streamed)", url);

        var destPath = getPath(url);
        var tempFile = Files.createTempFile(renderedResourcesRoot, url.getFilename(), ".tmp");
        ensureDirectoryExists(destPath.getParent());

        try (var os = new PrintWriter(Files.newOutputStream(tempFile))) {
            wo.write(os);
        }

        Files.move(tempFile, destPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        setFilePermissions(destPath);
    }


    private void setFilePermissions(Path destPath) throws IOException {
        Files.setPosixFilePermissions(destPath, filePermission);
    }

    private Path getPath(MemexNodeUrl url) {
        final Path path = Path.of(renderedResourcesRoot + url.toString()).normalize();

        if (!path.startsWith(renderedResourcesRoot)) {
            throw new IllegalStateException("URL " + url + " resulted in a path outside of root " + renderedResourcesRoot);
        }

        return path;
    }

    public interface WriteOperation {
        void write(PrintWriter w) throws IOException;
    }
}
