package net.csdn.common.statistics;

/**
 * User: william
 * Date: 11-11-2
 * Time: 下午3:21
 */
public class NormalCounter implements Counter {
    private volatile int count = 0;
    private volatile int total = 0;
    private EstimatedTimeThread timeEstimated;

    public synchronized Counter increment() {
        count++;
        total++;
        return this;
    }

    public Counter reset() {
        count = 0;
        return this;
    }

    public int count() {
        return count;
    }

    public static NormalCounter startCounter(long interval, Notice notice) {
        NormalCounter counter = new NormalCounter();
        counter.timeEstimated = new EstimatedTimeThread("monitor", interval, notice, counter);

        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        counter.timeEstimated.start();
        return counter;
    }

    public interface Notice {
        void noticeMe(EstimatedTimeThread estimatedTimeThread, Counter counter);
    }

    public static class EstimatedTimeThread extends Thread {
        final long interval;
        private Counter counter;
        volatile long startTime = System.currentTimeMillis();
        volatile boolean running = true;
        volatile long estimatedTimeInMillis;
        private Notice notice;

        public EstimatedTimeThread(String name, long interval, Notice notice, Counter counter) {
            super(name);
            this.interval = interval;
            this.notice = notice;
            this.counter = counter;
            setDaemon(true);
        }

        public long estimatedTimeInMillis() {
            return this.estimatedTimeInMillis;
        }

        public void reset() {
            startTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(interval);
                    estimatedTimeInMillis = System.currentTimeMillis() - startTime;
                    notice.noticeMe(this, counter);
                    reset();
                } catch (InterruptedException e) {
                    running = false;
                    return;
                }
            }
        }

    }

    @Override
    public long getGqs(long timeDistance) {
        return count() * 1000l / timeEstimated.estimatedTimeInMillis;
    }
}
