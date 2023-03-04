package nu.marginalia.memex.memex.system;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.memex.memex.system.git.MemexGitRepo;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

@Singleton
public class MemexSourceFileSystem {

    private final Path root;
    private final MemexGitRepo gitRepo;

    private static final Logger logger = LoggerFactory.getLogger(MemexSourceFileSystem.class);

    @Inject
    public MemexSourceFileSystem(@Named("memex-root") Path root,
                                 MemexGitRepo gitRepo) {
        this.root = root;
        this.gitRepo = gitRepo;
    }

    public void pullChanges() {
        gitRepo.pull();
    }

    public void replaceFile(MemexNodeUrl url, String text) throws IOException {
        var path = url.asAbsolutePath(root);
        Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        gitRepo.update(url);
    }

    public void createFile(MemexNodeUrl url, String text) throws IOException {
        var path = url.asAbsolutePath(root);
        logger.info("Writing {} ({}b)", path, text.length());

        Files.writeString(path, text, StandardOpenOption.CREATE_NEW);

        gitRepo.add(url);
    }

    public void createFile(MemexNodeUrl url, byte[] bytes) throws IOException {
        var path = url.asAbsolutePath(root);
        logger.info("Writing {} ({}b)", path, bytes.length);

        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);

        gitRepo.add(url);
    }

    public void delete(MemexNodeUrl url) throws IOException {
        var path = url.asAbsolutePath(root);

        logger.info("Delete {}", path);
        Files.delete(path);

        gitRepo.remove(url);
    }

    public void renameFile(MemexNodeUrl src, MemexNodeUrl dst) throws IOException {
        var srcPath = src.asAbsolutePath(root);
        var dstPath = dst.asAbsolutePath(root);

        if (!Files.exists(srcPath) || Files.exists(dstPath)) {
            throw new IOException("Could not rename " + src + " into " + dst);
        }

        Files.move(srcPath, dstPath, StandardCopyOption.ATOMIC_MOVE);
        gitRepo.rename(src, dst);
    }

    public byte[] getRaw(MemexNodeUrl url) throws IOException {
        logger.info("Getting raw file contents of {}", url);

        return Files.readAllBytes(url.asAbsolutePath(root));
    }
}
