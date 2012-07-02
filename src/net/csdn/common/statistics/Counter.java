package net.csdn.common.statistics;

/**
 * User: william
 * Date: 11-11-2
 * Time: 下午3:20
 */
public interface Counter {
    public long getGqs(long timeDistance);

    public Counter increment();
}
