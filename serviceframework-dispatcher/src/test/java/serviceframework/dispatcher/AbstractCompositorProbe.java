package serviceframework.dispatcher;

public abstract class AbstractCompositorProbe extends AppendingCompositor<String> {
    static {
        InitProbe.abstractCompositor.incrementAndGet();
    }
}
