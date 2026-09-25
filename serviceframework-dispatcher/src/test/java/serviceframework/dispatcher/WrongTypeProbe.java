package serviceframework.dispatcher;

/** 不是 Strategy / Processor / Compositor。static initializer 只能在真正初始化时跑。 */
public class WrongTypeProbe {
    static {
        InitProbe.wrongType.incrementAndGet();
    }

    public WrongTypeProbe() {
    }
}
