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
import org.apache.lucene.search.CSDNSimilarity;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: william
 * Date: 11-11-29
 * Time: 下午4:22
 */
public class RsyncIndexEngine implements Engine {
    protected final CSLogger logger = Loggers.getLogger(getClass());
    private final ReadWriteLock rwl = new ReentrantReadWriteLock();
    private volatile ReaderSearcherHolder readerSearcherHolder;
    private final Object refreshMutex = new Object();
    private final Index index;
    private final Environment environment;
    private NIOFSDirectory fsDirectory;
    private final Shard shard;

    private Settings settings;

    @Inject
    public RsyncIndexEngine(Settings settings,@Assisted Shard shard) {

        this.settings = settings;

        this.index = new Index(shard.index());
        this.environment = new Environment(settings);
        this.shard = shard;
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

            logger.debug("Starting engine:" + shard.toKey());

            try {
                  buildSearcher();
            } catch (Exception e) {
                throw new EngineException(index, "Failed to create engine", e);
            }

        } finally {
            rwl.writeLock().unlock();
        }
    }

     private void buildSearcher() throws EngineException {
        rwl.readLock().lock();
        try {
            logger.debug("Build searcher:"+this.shard.toString());
            IndexReader reader = IndexReader.open(fsDirectory, false);
            ExtendedIndexSearcher indexSearcher = new ExtendedIndexSearcher(reader,shard);
            indexSearcher.setSimilarity(new CSDNSimilarity());
            readerSearcherHolder = new ReaderSearcherHolder(indexSearcher);
        } catch (IOException e) {
            String message = MessageFormat.copyMessagesFromException(e);
            logger.error("Error when build searcher :"+this.shard.toString()+"==>"+message);
        } finally {
            rwl.readLock().unlock();
        }
    }

    @Override
    public void create(Create create) throws IOException {
          throw new EngineException(index,"RsyncIndexEngine do not support creating index");
    }

    @Override
    public void create(Create create, boolean isCreate) throws IOException {
          throw new EngineException(index,"RsyncIndexEngine do not support creating index");
    }

    @Override
    public void optimize() throws EngineException {
           throw new EngineException(index,"RsyncIndexEngine do not support index optimizing");
    }

    @Override
    public boolean safeClose() {
        return false;
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

    @Override
    public void flush(boolean full) {
         throw new EngineException(index,"RsyncIndexEngine do not support index flushing");
    }

    @Override
    public void deleteShard() {
         throw new EngineException(this.index,"RsyncIndexEngine do not support index deleting");
    }

    @Override
    public void refresh() {
         rwl.readLock().lock();

        try {
            synchronized (refreshMutex) {
               logger.debug("Refreshing:"+this.shard.toString());
                ExtendedIndexSearcher current = readerSearcherHolder.searcher();
                IndexReader newReader = current.getIndexReader().reopen(true);
                if (newReader != current.getIndexReader()) {
                    ExtendedIndexSearcher indexSearcher = new ExtendedIndexSearcher(newReader,shard);
                    indexSearcher.setSimilarity(new CSDNSimilarity());
                    readerSearcherHolder = (new ReaderSearcherHolder(indexSearcher));
                    current.close();
                }
                logger.debug("Finish refreshing:"+this.shard.toString());
            }
        } catch (Exception e) {
            String message = MessageFormat.copyMessagesFromException(e);
            logger.error("Error when refresh :"+this.shard.toString()+"==>"+message);
            throw new EngineException(index, "Fail to refresh");
        } finally {
            rwl.readLock().unlock();
        }
    }
}
