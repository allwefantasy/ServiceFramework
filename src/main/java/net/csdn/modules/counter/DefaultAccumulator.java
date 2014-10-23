package net.csdn.modules.counter;


import net.csdn.common.reflect.ReflectHelper;

/**
 * 7/21/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class DefaultAccumulator implements Accumulator {
    private boolean enable = enable();
    private Class clzz = null;

    public boolean enable() {
        try {
            clzz = Class.forName("csdn.pstats.client.StatsManager");
        } catch (ClassNotFoundException e) {
            return false;
        }
        return true;
    }

    @Override
    public void addStats(String module, String statsKey, int value) {
        if (enable) {
            ReflectHelper.staticMethod(clzz, "addStats", module, statsKey, value);
        }
    }

    @Override
    public void addStats(String module, String statsKey) {
        if (enable) {
            ReflectHelper.staticMethod(clzz, "addStats", module, statsKey);
        }
    }

    @Override
    public void stop() {
        if (enable) {
            ReflectHelper.staticMethod(clzz, "stop");
        }
    }

    @Override
    public void init() {

    }
}
