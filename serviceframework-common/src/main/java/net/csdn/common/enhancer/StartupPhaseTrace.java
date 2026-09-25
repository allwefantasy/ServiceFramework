package net.csdn.common.enhancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Caller-owned samples of named startup slices. The object lives on
 * {@link EnhancementContext#setAttribute(String, Object)} under {@link #ATTRIBUTE}.
 * When the attribute is absent, {@link #open} does not call {@code nanoTime}.
 * Samples store a name, elapsed nanoseconds and whether the slice was opened
 * inside another slice. They do not store settings, URLs or connection identity.
 * This is not a diagnostic switch and does not read bytecode.
 */
public final class StartupPhaseTrace {

    public static final String ATTRIBUTE = "serviceframework.startup.phases";

    private final List<Sample> samples = new ArrayList<Sample>();
    private int depth;

    public static StartupPhaseTrace lookup(EnhancementContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getAttribute(ATTRIBUTE);
        return value instanceof StartupPhaseTrace ? (StartupPhaseTrace) value : null;
    }

    /**
     * Starts a slice when a trace is attached. The inactive frame is shared and
     * its {@link Frame#close()} does not read the clock.
     */
    public static Frame open(EnhancementContext context, String name) {
        StartupPhaseTrace trace = lookup(context);
        if (trace == null) {
            return Frame.INACTIVE;
        }
        return trace.openFrame(name);
    }

    /**
     * Records a duration that the caller already measured. Nested when a frame
     * opened on this trace is still open.
     */
    public void record(String name, long elapsedNanos) {
        synchronized (this) {
            samples.add(new Sample(name, elapsedNanos, depth > 0));
        }
    }

    public List<Sample> samples() {
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<Sample>(samples));
        }
    }

    /**
     * Sum of samples that were not opened inside another sample. Nested samples
     * stay visible but must not be added again.
     */
    public long exclusiveNanos() {
        long sum = 0L;
        synchronized (this) {
            for (int i = 0; i < samples.size(); i++) {
                Sample sample = samples.get(i);
                if (!sample.nested) {
                    sum += sample.elapsedNanos;
                }
            }
        }
        return sum;
    }

    private Frame openFrame(String name) {
        boolean nested;
        synchronized (this) {
            nested = depth > 0;
            depth++;
        }
        return new Frame(this, name, System.nanoTime(), nested);
    }

    void finish(String name, long elapsedNanos, boolean nested) {
        synchronized (this) {
            if (depth > 0) {
                depth--;
            }
            samples.add(new Sample(name, elapsedNanos, nested));
        }
    }

    public static final class Sample {
        public final String name;
        public final long elapsedNanos;
        public final boolean nested;

        public Sample(String name, long elapsedNanos, boolean nested) {
            this.name = name;
            this.elapsedNanos = elapsedNanos;
            this.nested = nested;
        }
    }

    public static final class Frame {
        static final Frame INACTIVE = new Frame(null, null, 0L, false);

        private final StartupPhaseTrace trace;
        private final String name;
        private final long started;
        private final boolean nested;
        private boolean closed;

        Frame(StartupPhaseTrace trace, String name, long started, boolean nested) {
            this.trace = trace;
            this.name = name;
            this.started = started;
            this.nested = nested;
        }

        public void close() {
            if (trace == null || closed) {
                return;
            }
            closed = true;
            trace.finish(name, System.nanoTime() - started, nested);
        }
    }
}
