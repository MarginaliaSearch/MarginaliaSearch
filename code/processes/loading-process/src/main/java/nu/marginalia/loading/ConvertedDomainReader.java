package nu.marginalia.loading;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import nu.marginalia.converting.instruction.Instruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
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

        try (var or = new ObjectInputStream(new ZstdInputStream(new FileInputStream(path.toFile()), RecyclingBufferPool.INSTANCE))) {
            var object = or.readObject();
            if (object instanceof Instruction is) {
                ret.add(is);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        return ret;
    }

    public Iterator<Instruction> createIterator(Path path) throws IOException {
        var or = new ObjectInputStream(new ZstdInputStream(new BufferedInputStream(new FileInputStream(path.toFile())), RecyclingBufferPool.INSTANCE));

        return new Iterator<>() {
            Instruction next;
            @SneakyThrows
            @Override
            public boolean hasNext() {
                if (next != null)
                    return true;

                try {
                    next = (Instruction) or.readObject();
                    return true;
                }
                catch (java.io.EOFException ex) {
                    or.close();
                    return false;
                }
            }

            @Override
            public Instruction next() {
                if (next != null || hasNext()) {
                    var ret = next;
                    next = null;
                    return ret;
                }
                throw new IllegalStateException();
            }
        };
    }
}
