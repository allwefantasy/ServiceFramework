package net.csdn.modules.http.processor;

import java.lang.reflect.Method;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class ProcessInfo {
    public final long startTime = System.currentTimeMillis();
    public int status;
    public Method method;
    public long responseLength = 0;
}
