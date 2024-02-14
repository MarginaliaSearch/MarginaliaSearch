package nu.marginalia.executor.svc;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import nu.marginalia.actor.task.DownloadSampleActor;
import nu.marginalia.executor.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SideloadService {
    private final ExecutorActorControlService actorControlService;
    private static final Logger logger = LoggerFactory.getLogger(SideloadService.class);

    @Inject
    public SideloadService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    public void sideloadDirtree(RpcSideloadDirtree request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.ConvertDirtree(request.getSourcePath())
        );
    }

    public void sideloadReddit(RpcSideloadReddit request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.ConvertReddit(request.getSourcePath())
        );
    }

    public void sideloadWarc(RpcSideloadWarc request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.ConvertWarc(request.getSourcePath())
        );
    }

    public void sideloadEncyclopedia(RpcSideloadEncyclopedia request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.ConvertEncyclopedia(
                        request.getSourcePath(),
                        request.getBaseUrl()
                        ));
    }

    public void sideloadStackexchange(RpcSideloadStackexchange request) throws Exception {
        actorControlService.startFrom(ExecutorActor.CONVERT,
                new ConvertActor.ConvertStackexchange(request.getSourcePath())
        );
    }

    public RpcUploadDirContents listUploadDir() throws IOException {
        Path uploadDir = WmsaHome.getUploadDir();

        try (var items = Files.list(uploadDir)) {
            var builder = RpcUploadDirContents.newBuilder().setPath(uploadDir.toString());

            var iter = items.iterator();
            while (iter.hasNext()) {
                var path = iter.next();

                boolean isDir = Files.isDirectory(path);
                long size = isDir ? 0 : Files.size(path);
                var mtime = Files.getLastModifiedTime(path);

                builder.addEntriesBuilder()
                        .setName(path.toString())
                        .setIsDirectory(isDir)
                        .setLastModifiedTime(
                                LocalDateTime.ofInstant(mtime.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME))
                        .setSize(size);
            }

            return builder.build();
        }
    }

    public void downloadSampleData(RpcDownloadSampleData request) throws Exception {
        String sampleSet = request.getSampleSet();

        actorControlService.startFrom(ExecutorActor.DOWNLOAD_SAMPLE, new DownloadSampleActor.Run(sampleSet));
    }
}
