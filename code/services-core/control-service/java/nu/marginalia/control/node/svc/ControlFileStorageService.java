package nu.marginalia.control.node.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.control.Redirects;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jooby.Context;
import io.jooby.Jooby;

import static io.jooby.ParamSource.QUERY;
import static io.jooby.ParamSource.FORM;

import java.io.FileNotFoundException;
import java.io.IOException;
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
        jooby.post("/storage/{fid}/delete", ctx -> Redirects.redirectToStorage.render(flagFileForDeletionRequest(ctx)));

        jooby.post("/nodes/{id}/storage/{fid}/delete", this::deleteFileStorage);
        jooby.post("/nodes/{id}/storage/{fid}/enable", this::enableFileStorage);
        jooby.post("/nodes/{id}/storage/{fid}/disable", this::disableFileStorage);
        jooby.get("/nodes/{id}/storage/{fid}/transfer", this::downloadFileFromStorage);

    }

    public String redirectToOverview(int nodeId) {
        try {
            return new Redirects.HtmlRedirect("/nodes/"+nodeId).render(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String redirectToOverview(Context ctx) {
        return redirectToOverview(Integer.parseInt(ctx.path("id").value()));
    }

    private Object deleteFileStorage(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        int fileId = Integer.parseInt(ctx.path("fid").value());

        fileStorageService.flagFileForDeletion(new FileStorageId(fileId));

        return redirectToOverview(ctx);
    }

    public Object downloadFileFromStorage(Context ctx) throws IOException, SQLException {
        var fileStorageId = FileStorageId.parse(ctx.path("fid").value());

        String path = ctx.lookup("path", QUERY, FORM).valueOrNull();

        ctx.setResponseHeader("content-disposition", "attachment; filename=\""+path+"\"");

        if (path.endsWith(".txt") || path.endsWith(".log"))
            ctx.setResponseType("text/plain");
        else
            ctx.setResponseType("application/octet-stream");

        var storage = fileStorageService.getStorage(fileStorageId);

        try (var urlStream = executorClient.remoteFileURL(storage, path).openStream();
             var output = ctx.responseStream()) {
            urlStream.transferTo(output);
        }
        catch (FileNotFoundException ex) {
            logger.warn("File {} not found in storage {} (404)", path, fileStorageId);
            throw ex;
        }

        return "";
    }

    private Object enableFileStorage(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        FileStorageId fileId = new FileStorageId(Integer.parseInt(ctx.path("fid").value()));

        var storage = fileStorageService.getStorage(fileId);
        if (storage.type() == FileStorageType.CRAWL_DATA) {
            fileStorageService.disableFileStorageOfType(nodeId, storage.type());
        }

        fileStorageService.enableFileStorage(fileId);

        return "";
    }

    private Object disableFileStorage(Context ctx) throws SQLException {
        int nodeId = Integer.parseInt(ctx.path("id").value());
        int fileId = Integer.parseInt(ctx.path("fid").value());

        fileStorageService.disableFileStorage(new FileStorageId(fileId));

        return "";
    }

    public Object flagFileForDeletionRequest(Context ctx) throws SQLException {
        FileStorageId fid = new FileStorageId(Long.parseLong(ctx.path("fid").value()));
        fileStorageService.flagFileForDeletion(fid);
        return "";
    }


}
