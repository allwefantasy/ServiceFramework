package net.csdn.trace;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class Trace {
    private final static ThreadLocal<TraceContext> holder = new ThreadLocal<TraceContext>();

    public static TraceContext get() {
        return holder.get();
    }

    public static void set(TraceContext traceContext) {
        holder.set(traceContext);
    }

    public static TraceContext clean() {
        TraceContext temp = get();
        holder.remove();
        return temp;
    }
}

