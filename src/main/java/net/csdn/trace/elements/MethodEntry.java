package net.csdn.trace.elements;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class MethodEntry {
    long enterTime;
    String enterMethod;

    public MethodEntry(long enterTime, String enterMethod) {
        this.enterTime = enterTime;
        this.enterMethod = enterMethod;
    }

    public long getEnterTime() {
        return enterTime;
    }


    public String getEnterMethod() {
        return enterMethod;
    }


}
