package serviceframework.dispatcher;

public class NoDefaultCtorProbe extends TrackStrategy {
    static {
        InitProbe.noDefaultCtor.incrementAndGet();
    }

    public NoDefaultCtorProbe(int ignored) {
    }
}
