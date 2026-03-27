package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.Redirects;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

@Singleton
public class ControlFileStorageService {
    private final FileStorageService fileStorageService;
    private final ExecutorClient executorClient;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public ControlFileStorageService(FileStorageService fileStorageService,
                                     ExecutorClient executorClient)
    {
        this.fileStorageService = fileStorageService;
        this.executorClient = executorClient;
    }

    public void register(Jooby jooby) throws IOException {
        jooby.post("/storage/{fid}/delete", this::flagFileForDeletionRequest);

        jooby.post("/nodes/{id}/storage/{fid}/delete", this::deleteFileStorage);
        jooby.post("/nodes/{id}/storage/{fid}/enable", this::enableFileStorage);
        jooby.post("/nodes/{id}/storage/{fid}/disable", this::disableFileStorage);
        jooby.get("/nodes/{id}/storage/{fid}/transfer", this::downloadFileFromStorage);
    }

    private Object flagFileForDeletionRequest(Context ctx) throws SQLException {
        FileStorageId fid = new FileStorageId(Long.parseLong(ctx.path("fid").value()));
        fileStorageService.flagFileForDeletion(fid);

        ctx.setResponseType(MediaType.html);
        return Redirects.redirectToStorage.render(null);
    }

    private Object deleteFileStorage(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        int fileId = Integer.parseInt(ctx.path("fid").value());

        fileStorageService.flagFileForDeletion(new FileStorageId(fileId));

        ctx.setResponseType(MediaType.html);
        return new Redirects.HtmlRedirect("/nodes/" + nodeId).render(null);
    }

    private Object enableFileStorage(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        FileStorageId fileId = new FileStorageId(Integer.parseInt(ctx.path("fid").value()));

        FileStorage storage = fileStorageService.getStorage(fileId);
        if (storage.type() == FileStorageType.CRAWL_DATA) {
            fileStorageService.disableFileStorageOfType(nodeId, storage.type());
        }

        fileStorageService.enableFileStorage(fileId);

        ctx.setResponseType(MediaType.html);
        return "";
    }

    private Object disableFileStorage(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        int fileId = Integer.parseInt(ctx.path("fid").value());

        fileStorageService.disableFileStorage(new FileStorageId(fileId));

        ctx.setResponseType(MediaType.html);
        return "";
    }

    public Object downloadFileFromStorage(Context ctx) throws IOException, SQLException {
        FileStorageId fileStorageId = FileStorageId.parse(ctx.path("fid").value());

        String path = ctx.query("path").valueOrNull();

        ctx.setResponseHeader("content-disposition", "attachment; filename=\""+path+"\"");

        if (path.endsWith(".txt") || path.endsWith(".log"))
            ctx.setResponseType(MediaType.valueOf("text/plain"));
        else
            ctx.setResponseType(MediaType.valueOf("application/octet-stream"));

        FileStorage storage = fileStorageService.getStorage(fileStorageId);

        try (InputStream urlStream = executorClient.remoteFileURL(storage, path).openStream()) {
            urlStream.transferTo(ctx.responseStream());
        }

        return ctx;
    }

}
