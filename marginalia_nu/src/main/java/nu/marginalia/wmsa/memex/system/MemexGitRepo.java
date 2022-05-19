package nu.marginalia.wmsa.memex.system;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

@Singleton
public class MemexGitRepo {

    private final Git git;
    private final Logger logger = LoggerFactory.getLogger(MemexGitRepo.class);

    @Inject
    public MemexGitRepo(@Named("memex-root") Path root) throws IOException {

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();

        SshSessionFactory.setInstance(new JschConfigSessionFactory() {
            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch defaultJSch = super.createDefaultJSch(fs);
                defaultJSch.addIdentity("/var/lib/wmsa/.ssh/id_rsa");
                return defaultJSch;
            }
        });

        Repository repository = repositoryBuilder.setGitDir(root.resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .setMustExist(true)
                .build();

        git = new Git(repository);

        pull();
    }

    public void pull() {
        try {
            git.pull().call();
        }
        catch (GitAPIException ex) {
            logger.error("Git operation failed", ex);
        }
    }

    public void remove(MemexNodeUrl url) {
        try {
            git.rm()
                    .addFilepattern(filePattern(url))
                    .call();

            commit("Removing " + url);
            push();
        }
        catch (GitAPIException ex) {
            logger.error("Git operation failed", ex);
        }
    }

    public void add(MemexNodeUrl url) {
        try {
            git.add()
                    .addFilepattern(filePattern(url))
                    .call();

            commit("Adding " + url);
            push();


        }
        catch (GitAPIException ex) {
            logger.error("Git operation failed", ex);
        }
    }
    public void update(MemexNodeUrl url) {
        try {
            git.add()
                    .setUpdate(true)
                    .addFilepattern(filePattern(url))
                    .call();

            commit("Update " + url);
            push();


        }
        catch (GitAPIException ex) {
            logger.error("Git operation failed", ex);
        }
    }


    public void rename(MemexNodeUrl src, MemexNodeUrl dst) {
        try {
            git.rm().addFilepattern(filePattern(src)).call();
            git.add().addFilepattern(filePattern(dst)).call();
            commit("Renaming " + src + " into " + dst);
            push();
        }
        catch (GitAPIException ex) {
            logger.error("Git operation failed", ex);
        }
    }

    private void push() throws GitAPIException {
        git.push().call();
    }

    private void commit(String message) throws GitAPIException {
        git.commit()
                .setCommitter("marginalia", "system@marginalia.nu")
                .setMessage("Changes from web gui: " + message)
                .call();
    }

    private String filePattern(MemexNodeUrl url) {
        return url.asRelativePath().toString().replaceAll("^/+", "");
    }

}
