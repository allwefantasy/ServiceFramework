package org.apache.lucene.index;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import net.csdn.cluster.routing.Shard;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.logging.support.MessageFormat;
import net.csdn.common.lucene.ReaderSearcherHolder;
import net.csdn.common.settings.Settings;
import net.csdn.env.Environment;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.gateway.GatewayData;
import net.sf.json.JSONObject;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.CSDNSimilarity;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * User: william
 * Date: 11-9-13
 * Time: 下午2:29
 */
public class IndexEngine implements Engine {
    protected final CSLogger logger = Loggers.getLogger(getClass());

    private final ReadWriteLock rwl = new ReentrantReadWriteLock();
    private IndexWriter indexWriter;
    private volatile ReaderSearcherHolder readerSearcherHolder;
    private volatile boolean closed = false;
    private volatile boolean dirty = false;
    private final AtomicReference<Searcher> indexingSearcher = new AtomicReference<Searcher>();
    private final AtomicBoolean flushing = new AtomicBoolean();
    private final Object refreshMutex = new Object();
    private final Object flushMutex = new Object();
    private final AtomicBoolean optimizeMutex = new AtomicBoolean();
    private final Index index;

    private NIOFSDirectory fsDirectory;
    private volatile int counter = 0;
    private final Shard shard;

    private GatewayData gatewayData;
    private Settings settings;
    private Environment environment;
    private AnalyzerService analyzerService;

    @Inject
    public IndexEngine(Settings settings, Environment environment, AnalyzerService analyzerService, GatewayData gatewayData, @Assisted Shard _shard) {
        this.gatewayData = gatewayData;
        this.settings = settings;
        this.environment = environment;
        this.analyzerService = analyzerService;

        this.shard = _shard;
        this.index = new Index(shard.index());

        File indexDir = null;
        indexDir = new File(environment.dataFile().getPath(), index.getName() + "/" + shard.shardId());
        if (!indexDir.exists()) indexDir.mkdirs();
        try {
            fsDirectory = new NIOFSDirectory(indexDir, new SimpleFSLockFactory());
        } catch (IOException e) {
            //ignore
        }
        start();

    }


    @Override
    public void start() throws EngineException {
        rwl.writeLock().lock();
        try {
            if (indexWriter != null) {
                throw new EngineAlreadyStartedException(index);
            }

            logger.debug("Starting engine:" + shard.toKey());

            try {
                this.indexWriter = createWriter();
            } catch (IOException e) {
                throw new EngineException(index, "Failed to create engine", e);
            }
            buildSearcher();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    private IndexWriter createWriter() throws IOException {
        IndexWriter indexWriter = null;
        try {
            if (IndexWriter.isLocked(fsDirectory)) {
                logger.warn("index [" + shard.toString() + "] is locked, releasing lock");
                IndexWriter.unlock(fsDirectory);
            }
            boolean create = !IndexReader.indexExists(fsDirectory);
            IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_32, analyzerService.defaultAnalyzer());
            config.setOpenMode(create ? IndexWriterConfig.OpenMode.CREATE : IndexWriterConfig.OpenMode.APPEND);
//            config.setIndexDeletionPolicy(deletionPolicy);
//            config.setMergeScheduler(mergeScheduler.newMergeScheduler());
//            config.setMergePolicy(mergePolicyProvider.newMergePolicy());
            config.setSimilarity(new CSDNSimilarity());
//            config.setRAMBufferSizeMB(indexingBufferSize.mbFrac());
//            config.setTermIndexInterval(termIndexInterval);
//            config.setReaderTermsIndexDivisor(termIndexDivisor);
//            config.setMaxThreadStates(indexConcurrency);
            indexWriter = new IndexWriter(fsDirectory, config);
            logger.debug("create IndexWriter:" + shard.toKey());
        } catch (IOException e) {
            safeClose();
            throw e;
        }

        return indexWriter;
    }

    @Override
    public void create(Create create) throws IOException {
        rwl.readLock().lock();
        try {
            innerCreate(create);
            dirty = true;
            counter++;
        } finally {
            rwl.readLock().unlock();
        }
    }

    @Override
    public void create(Create create, boolean isCreate) throws IOException {
        rwl.readLock().lock();
        try {
            innerCreate(create, isCreate);
            dirty = true;
            counter++;
        } finally {
            rwl.readLock().unlock();
        }
    }

    @Override
    public void optimize() throws EngineException {
        if (optimizeMutex.compareAndSet(false, true)) {
            rwl.readLock().lock();
            try {

                if (indexWriter == null) {
                    throw new EngineException(index, "have already been closed. cannot optimize");
                }

                logger.debug("Optimizing:" + this.shard.toString());
                indexWriter.optimize();
                logger.debug("finish optimizing:" + this.shard.toString());


            } catch (CorruptIndexException e) {
                e.printStackTrace();
                throw new EngineException(index, "CorruptIndexException", e);
            } catch (IOException e) {
                e.printStackTrace();
                throw new EngineException(index, "IOException", e);
            } finally {
                rwl.readLock().unlock();
                optimizeMutex.set(false);

            }
        }
    }


    private void innerCreate(Create create) throws IOException {
        synchronized (create.uid()) {
            indexWriter.updateDocument(new Term("_uid", create.uid()), create.doc());
        }
    }

    private void innerCreate(Create create, boolean isCreate) throws IOException {
        synchronized (create.uid()) {
            if (isCreate) {
                indexWriter.addDocument(create.doc());
            } else {
                indexWriter.updateDocument(new Term("_uid", create.uid()), create.doc());
            }
        }
    }

    @Override
    public boolean safeClose() {
        if (indexWriter == null) {
            return true;
        }
        try {
            readerSearcherHolder.searcher().close();
            indexWriter.close();
            deleteDir(new File(environment.dataFile().getPath() + "/" + index.getName() + "/" + shard.shardId()));
            logger.debug("Close IndexWriter:" + shard.toKey());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void deleteDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory())
            return; // 检查参数
        for (File file : dir.listFiles()) {
            if (file.isFile())
                file.delete(); // 删除所有文件
            else if (file.isDirectory())
                deleteDir(file); // 递规的方式删除文件夹
        }
        dir.delete();// 删除目录本身
    }

    @Override
    public ReaderSearcherHolder readerSearcherHolder() {
        rwl.readLock().lock();
        try {
            if (readerSearcherHolder != null) {
                return readerSearcherHolder;
            } else {
                buildSearcher();
                return readerSearcherHolder;
            }

        } finally {
            rwl.readLock().unlock();
        }

    }

    private void buildSearcher() throws EngineException {
        rwl.readLock().lock();
        try {
            logger.debug("Build searcher:" + this.shard.toString());
            IndexReader reader = IndexReader.open(indexWriter, false);
            ExtendedIndexSearcher indexSearcher = new ExtendedIndexSearcher(reader, shard);
            indexSearcher.setSimilarity(new CSDNSimilarity());
            readerSearcherHolder = new ReaderSearcherHolder(indexSearcher);
        } catch (IOException e) {
            String message = MessageFormat.copyMessagesFromException(e);
            logger.error("Error when build searcher :" + this.shard.toString() + "==>" + message);
        } finally {
            rwl.readLock().unlock();
        }
    }


    @Override
    public void flush(boolean full) {
        rwl.writeLock().lock();
        try {
            synchronized (flushMutex) {
                if (full) {
                    indexWriter.close();
                    indexWriter = createWriter();
                    buildSearcher();
                } else {
                    logger.debug("Flushing:" + this.shard.toString());
                    indexWriter.commit();
                    logger.debug("Finish flushing:" + this.shard.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("index [" + this.shard.index() + "] commit ERROR:" + e.getMessage());
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void deleteShard() {
        rwl.readLock().lock();
        try {
            safeClose();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rwl.readLock().unlock();
        }
    }

    @Override
    public void refresh() {
        rwl.readLock().lock();

        try {
            synchronized (refreshMutex) {
                logger.debug("Refreshing:" + this.shard.toString());
                ExtendedIndexSearcher current = readerSearcherHolder.searcher();
                IndexReader newReader = current.getIndexReader().reopen(true);
                if (newReader != current.getIndexReader()) {
                    ExtendedIndexSearcher indexSearcher = new ExtendedIndexSearcher(newReader, shard);
                    indexSearcher.setSimilarity(new CSDNSimilarity());
                    readerSearcherHolder = (new ReaderSearcherHolder(indexSearcher));
                    current.close();
                }
                logger.debug("Finish refreshing:" + this.shard.toString());
            }
        } catch (Exception e) {
            String message = MessageFormat.copyMessagesFromException(e);
            logger.error("Error when refresh :" + this.shard.toString() + "==>" + message);
            throw new EngineException(index, "Fail to refresh");
        } finally {
            rwl.readLock().unlock();
        }
    }


    public static Create newCreate(Mapper mapper, JSONObject source) {
        return new Create(mapper, source);
    }

    public static Create newCreate(Document document) {
        return new Create(document);
    }

}
