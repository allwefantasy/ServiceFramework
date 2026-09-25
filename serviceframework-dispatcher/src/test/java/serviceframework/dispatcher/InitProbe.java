package serviceframework.dispatcher;

import java.util.concurrent.atomic.AtomicInteger;

/** 记录 fixture 的 static initializer 有没有跑过。 */
public final class InitProbe {
    public static final AtomicInteger wrongType = new AtomicInteger();
    public static final AtomicInteger abstractStrategy = new AtomicInteger();
    public static final AtomicInteger abstractProcessor = new AtomicInteger();
    public static final AtomicInteger abstractCompositor = new AtomicInteger();
    public static final AtomicInteger packagePrivate = new AtomicInteger();
    public static final AtomicInteger noDefaultCtor = new AtomicInteger();
    public static final AtomicInteger good = new AtomicInteger();
    public static volatile ClassLoader loadedBy;

    private InitProbe() {
    }

    public static void reset() {
        wrongType.set(0);
        abstractStrategy.set(0);
        abstractProcessor.set(0);
        abstractCompositor.set(0);
        packagePrivate.set(0);
        noDefaultCtor.set(0);
        good.set(0);
        loadedBy = null;
    }
}
