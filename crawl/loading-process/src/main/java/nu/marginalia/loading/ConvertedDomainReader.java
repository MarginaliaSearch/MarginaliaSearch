package nu.marginalia.loading;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConvertedDomainReader {
    private static final Logger logger = LoggerFactory.getLogger(ConvertedDomainReader.class);
    private final Gson gson;

    @Inject
    public ConvertedDomainReader(Gson gson) {
        this.gson = gson;
    }

    public List<Instruction> read(Path path, int cntHint) throws IOException {
        List<Instruction> ret = new ArrayList<>(cntHint);

        try (var br = new BufferedReader(new InputStreamReader(new ZstdInputStream(new BufferedInputStream(new FileInputStream(path.toFile())))))) {
            String line;
            for (;;) {
                line = br.readLine();

                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                var parts=  line.split(" ", 2);
                var type = InstructionTag.valueOf(parts[0]).clazz;

                try {
                    ret.add(gson.fromJson(parts[1], type));
                }
                catch (NullPointerException|JsonParseException ex) {
                    logger.warn("Failed to deserialize {} {}", type.getSimpleName(), StringUtils.abbreviate(parts[1], 255));
                    logger.warn("Json error", ex);
                }
            }
        }

        return ret;
    }
}
