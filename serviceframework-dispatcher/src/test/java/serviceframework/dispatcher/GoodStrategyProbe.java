package serviceframework.dispatcher;

public class GoodStrategyProbe extends TrackStrategy {
    static {
        InitProbe.good.incrementAndGet();
        InitProbe.loadedBy = GoodStrategyProbe.class.getClassLoader();
    }
}
