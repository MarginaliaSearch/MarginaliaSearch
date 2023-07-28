package nu.marginalia.converting.sideload;

import com.google.gson.Gson;
import com.google.inject.Inject;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;

import java.nio.file.Path;
import java.sql.SQLException;

public class SideloadSourceFactory {
    private final Gson gson;
    private final HtmlDocumentProcessorPlugin htmlProcessorPlugin;

    @Inject
    public SideloadSourceFactory(Gson gson, HtmlDocumentProcessorPlugin htmlProcessorPlugin) {
        this.gson = gson;
        this.htmlProcessorPlugin = htmlProcessorPlugin;
    }

    public SideloadSource sideloadEncyclopediaMarginaliaNu(Path pathToDbFile) throws SQLException {
        return new EncyclopediaMarginaliaNuSideloader(pathToDbFile, gson, htmlProcessorPlugin);
    }
}
