package serviceframework.dispatcher;

public abstract class AbstractStrategyProbe extends TrackStrategy {
    static {
        InitProbe.abstractStrategy.incrementAndGet();
    }
}
