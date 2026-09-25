package serviceframework.dispatcher;

public abstract class AbstractProcessorProbe extends NamedProcessor<String> {
    static {
        InitProbe.abstractProcessor.incrementAndGet();
    }
}
