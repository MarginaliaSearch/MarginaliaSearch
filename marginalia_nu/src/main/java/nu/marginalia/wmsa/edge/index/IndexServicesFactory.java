package nu.marginalia.wmsa.edge.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.index.conversion.ConversionUnnecessaryException;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexPartitioner;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexPreconverter;
import nu.marginalia.wmsa.edge.index.journal.SearchIndexJournalWriterImpl;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.reader.SearchIndex;
import nu.marginalia.wmsa.edge.index.reader.SearchIndexReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.concurrent.Callable;

import static nu.marginalia.wmsa.edge.index.EdgeIndexService.DYNAMIC_BUCKET_LENGTH;

@Singleton
public class IndexServicesFactory {
    private final Path tmpFileDir;
    private final EdgeDomainBlacklist domainBlacklist;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PartitionedDataFile writerIndexFile;
    private final RootDataFile keywordLexiconFile;
    private final PartitionedDataFile preconverterOutputFile;
    private final DoublePartitionedDataFile indexReadWordsFile;
    private final DoublePartitionedDataFile indexReadUrlsFile;
    private final DoublePartitionedDataFile indexWriteWordsFile;
    private final DoublePartitionedDataFile indexWriteUrlsFile;
    private volatile static KeywordLexicon keywordLexicon;
    private final Long dictionaryHashMapSize;
    private final SearchIndexPartitioner partitioner;

    @Inject
    public IndexServicesFactory(
            @Named("tmp-file-dir") Path tmpFileDir,
            @Named("partition-root-slow") Path partitionRootSlow,
            @Named("partition-root-slow-tmp") Path partitionRootSlowTmp,
            @Named("partition-root-fast") Path partitionRootFast,
            @Named("edge-writer-page-index-file") String writerIndexFile,
            @Named("edge-writer-dictionary-file") String keywordLexiconFile,
            @Named("edge-index-read-words-file") String indexReadWordsFile,
            @Named("edge-index-read-urls-file") String indexReadUrlsFile,
            @Named("edge-index-write-words-file") String indexWriteWordsFile,
            @Named("edge-index-write-urls-file") String indexWriteUrlsFile,
            @Named("edge-dictionary-hash-map-size") Long dictionaryHashMapSize,
            EdgeDomainBlacklist domainBlacklist,
            SearchIndexPartitioner partitioner
            ) {

        this.tmpFileDir = tmpFileDir;
        this.dictionaryHashMapSize = dictionaryHashMapSize;
        this.domainBlacklist = domainBlacklist;

        this.writerIndexFile = new PartitionedDataFile(partitionRootSlow, writerIndexFile);
        this.keywordLexiconFile = new RootDataFile(partitionRootSlow, keywordLexiconFile);
        this.indexReadWordsFile = new DoublePartitionedDataFile(partitionRootFast, indexReadWordsFile);
        this.indexReadUrlsFile = new DoublePartitionedDataFile(partitionRootFast, indexReadUrlsFile);
        this.indexWriteWordsFile = new DoublePartitionedDataFile(partitionRootFast, indexWriteWordsFile);
        this.indexWriteUrlsFile = new DoublePartitionedDataFile(partitionRootFast, indexWriteUrlsFile);
        this.preconverterOutputFile = new PartitionedDataFile(partitionRootSlowTmp, "preconverted.dat");
        this.partitioner = partitioner;
    }

    public SearchIndexJournalWriterImpl getIndexWriter(int idx) {
        return new SearchIndexJournalWriterImpl(getKeywordLexicon(), writerIndexFile.get(idx));
    }

    @SneakyThrows
    public KeywordLexicon getKeywordLexicon() {
        if (keywordLexicon == null) {
            final var journal = new KeywordLexiconJournal(keywordLexiconFile.get());
            keywordLexicon = new KeywordLexicon(journal,
                    new DictionaryHashMap(dictionaryHashMapSize));
        }
        return keywordLexicon;
    }

    @SneakyThrows
    public KeywordLexiconReadOnlyView getDictionaryReader() {
        return new KeywordLexiconReadOnlyView(getKeywordLexicon());

    }

    public void convertIndex(int id, IndexBlock block) throws ConversionUnnecessaryException, IOException {
        var converter = new SearchIndexConverter(block, id, tmpFileDir,
                preconverterOutputFile.get(id),
                indexWriteWordsFile.get(id, block.id),
                indexWriteUrlsFile.get(id, block.id),
                partitioner,
                domainBlacklist
                );
        converter.convert();
    }

    @SneakyThrows
    public SearchIndexPreconverter getIndexPreconverter() {
        File[] outputFiles = new File[DYNAMIC_BUCKET_LENGTH+1];
        for (int i = 0; i < outputFiles.length; i++) {
            outputFiles[i] = getPreconverterOutputFile(i);
        }
        return new SearchIndexPreconverter(writerIndexFile.get(0),
                outputFiles,
                partitioner,
                domainBlacklist
        );
    }

    private File getPreconverterOutputFile(int i) {
        return preconverterOutputFile.get(i);
    }

    @SneakyThrows
    public SearchIndexReader getIndexReader(int id) {
        EnumMap<IndexBlock, SearchIndex> indexMap = new EnumMap<>(IndexBlock.class);
        for (IndexBlock block : IndexBlock.values()) {
            try {
                indexMap.put(block, createSearchIndex(id, block));
            }
            catch (Exception ex) {
                logger.error("Could not create index {}-{}", id, block);
            }
        }
        return new SearchIndexReader(indexMap);
    }

    private SearchIndex createSearchIndex(int bucketId, IndexBlock block) {
        try {
            return new SearchIndex("IndexReader"+bucketId+":"+ block.name(),
                    indexReadUrlsFile.get(bucketId, block.id),
                    indexReadWordsFile.get(bucketId, block.id));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Callable<Boolean> switchFilesJob(int id) {
        return () -> {
            for (int block = 0; block < IndexBlock.values().length; block++) {
                if (Files.exists(indexWriteWordsFile.get(id, block).toPath()) &&
                    Files.exists(indexWriteUrlsFile.get(id, block).toPath())) {
                    Files.move(
                            indexWriteWordsFile.get(id, block).toPath(),
                            indexReadWordsFile.get(id, block).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    Files.move(
                            indexWriteUrlsFile.get(id, block).toPath(),
                            indexReadUrlsFile.get(id, block).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        };
    }

    public EdgeIndexBucket createIndexBucket(int id) {
        return new EdgeIndexBucket(this, new EdgeIndexControl(this), id);
    }
}

class RootDataFile {
    private final Path partition;
    private final String pattern;

    RootDataFile(Path partition, String pattern) {
        this.partition = partition;
        this.pattern = pattern;
    }

    public File get() {
        return partition.resolve(pattern).toFile();
    }
}


class PartitionedDataFile {
    private final Path partition;
    private final String pattern;

    PartitionedDataFile(Path partition, String pattern) {
        this.partition = partition;
        this.pattern = pattern;
    }

    public File get(int id) {
        Path partitionDir = partition.resolve(Integer.toString(id));
        if (!partitionDir.toFile().exists()) {
            partitionDir.toFile().mkdir();
        }
        return partitionDir.resolve(pattern).toFile();
    }
}

class DoublePartitionedDataFile {
    private final Path partition;
    private final String pattern;

    DoublePartitionedDataFile(Path partition, String pattern) {
        this.partition = partition;
        this.pattern = pattern;
    }

    public File get(int id, int id2) {
        Path partitionDir = partition.resolve(Integer.toString(id));

        if (!partitionDir.toFile().exists()) {
            partitionDir.toFile().mkdir();
        }
        partitionDir = partitionDir.resolve(Integer.toString(id2));
        if (!partitionDir.toFile().exists()) {
            partitionDir.toFile().mkdir();
        }

        return partitionDir.resolve(pattern).toFile();
    }

}