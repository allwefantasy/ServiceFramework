package net.csdn.modules.counter;

import com.google.inject.ImplementedBy;

/**
 * 7/21/14 WilliamZhu(allwefantasy@gmail.com)
 */
@ImplementedBy(DefaultAccumulator.class)
public interface Accumulator {

    public void addStats(String module, String statsKey, int value);

    public void addStats(String module, String statsKey);

    public void stop();

    public void init();

}
