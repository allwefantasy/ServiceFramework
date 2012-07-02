package net.csdn.common.statistics;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.unit.TimeValue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * User: william
 * Date: 11-10-20
 * Time: 上午11:47
 */
public class DJPCounter implements Counter {

    private long startUp = System.currentTimeMillis();
    private int interval = 1000 * 60 * 1;
    private int maxDistance = 1000 * 60 * 60 * 24;
    private volatile int budges = (int) (maxDistance / interval);
    private volatile AtomicLong[] timeCounter = new AtomicLong[budges];
    private volatile int current_budge = 0;

    private CSLogger logger = Loggers.getLogger(getClass());

    public DJPCounter() {
        for (int i = 0; i < timeCounter.length; i++) {
            timeCounter[i] = new AtomicLong(0);
        }
        logger.info("计数器初始化完毕");
    }

    public synchronized Counter increment() {
        long now = System.currentTimeMillis();
        int budget = new Long((now - startUp) % (budges)).intValue();
        if (current_budge != budget) {
            current_budge = budget;
            timeCounter[current_budge].set(0);
        }
        timeCounter[current_budge].incrementAndGet();
        return this;
    }

    public long getGqs(long timeDistance) {
        if (timeDistance > maxDistance) {
            timeDistance = maxDistance;
        }
        int final_count = 0;
        int spans = (int) (timeDistance / interval);
        int temp = current_budge - 1;
        for (int i = 0; i < spans; i++) {
            if (temp < 0) {
                final_count += timeCounter[budges + temp].get();
            } else {
                final_count += timeCounter[temp].get();
            }
            temp -= 1;
        }
        return final_count / TimeValue.timeValueMillis(timeDistance).seconds();
    }

}
