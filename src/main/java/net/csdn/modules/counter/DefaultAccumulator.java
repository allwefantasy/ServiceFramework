package net.csdn.modules.counter;


/**
 * 7/21/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class DefaultAccumulator implements Accumulator {
    private boolean enable = enable();

    public boolean enable() {
        try {
            Class.forName("csdn.pstats.client.StatsManager");
        } catch (ClassNotFoundException e) {
            return false;
        }
        return true;
    }

    @Override
    public void addStats(String module, String statsKey, int value) {
        if (enable) {
            csdn.pstats.client.StatsManager.addStats(module, statsKey, value);
        }
    }

    @Override
    public void addStats(String module, String statsKey) {
        if (enable) {
            csdn.pstats.client.StatsManager.addStats(module, statsKey);
        }
    }

    @Override
    public void stop() {
        if (enable) {
            csdn.pstats.client.StatsManager.stop();
        }
    }

    @Override
    public void init() {

    }
}
