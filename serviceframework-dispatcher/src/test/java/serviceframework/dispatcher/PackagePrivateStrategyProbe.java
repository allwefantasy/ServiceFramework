package serviceframework.dispatcher;

class PackagePrivateStrategyProbe extends TrackStrategy {
    static {
        InitProbe.packagePrivate.incrementAndGet();
    }

    public PackagePrivateStrategyProbe() {
    }
}
