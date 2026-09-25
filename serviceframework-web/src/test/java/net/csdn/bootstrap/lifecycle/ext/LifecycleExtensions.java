package net.csdn.bootstrap.lifecycle.ext;

import javassist.CtClass;
import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.FrameworkExtension;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.settings.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LifecycleExtensions {
    public static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<String>());

    private LifecycleExtensions() {
    }

    public static void reset() {
        EVENTS.clear();
    }

    public static final class Recording implements FrameworkExtension {
        private boolean closed;

        @Override
        public String id() {
            return "recording";
        }

        @Override
        public void start(Settings settings, ApplicationContext context) {
            EVENTS.add("recording-start");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            EVENTS.add("recording-close");
        }
    }

    public static final class CycleLeft implements FrameworkExtension {
        @Override
        public String id() {
            return "cycle-left";
        }

        @Override
        public List<String> provides() {
            return Collections.singletonList("cap.left");
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList("cap.right");
        }

        @Override
        public void start(Settings settings, ApplicationContext context) {
            EVENTS.add("cycle-left-start");
        }
    }

    public static final class CycleRight implements FrameworkExtension {
        @Override
        public String id() {
            return "cycle-right";
        }

        @Override
        public List<String> provides() {
            return Collections.singletonList("cap.right");
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList("cap.left");
        }

        @Override
        public void start(Settings settings, ApplicationContext context) {
            EVENTS.add("cycle-right-start");
        }
    }

    public static final class NeedsMissing implements FrameworkExtension {
        @Override
        public String id() {
            return "needs-missing";
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList("missing.capability");
        }

        @Override
        public void start(Settings settings, ApplicationContext context) {
            EVENTS.add("missing-start");
        }
    }

    public static final class DuplicateOne implements FrameworkExtension {
        @Override
        public String id() {
            return "duplicate-one";
        }

        @Override
        public List<String> provides() {
            return Collections.singletonList("shared.capability");
        }
    }

    public static final class DuplicateTwo implements FrameworkExtension {
        @Override
        public String id() {
            return "duplicate-two";
        }

        @Override
        public List<String> provides() {
            return Collections.singletonList("shared.capability");
        }
    }

    public static final class StartFirst implements FrameworkExtension {
        private boolean closed;

        @Override
        public String id() {
            return "start-first";
        }

        @Override
        public void start(Settings settings, ApplicationContext context) {
            EVENTS.add("start-first");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            EVENTS.add("close-first");
            throw new IllegalStateException("close-first-failed");
        }
    }

    public static final class StartSecond implements FrameworkExtension {
        private boolean closed;

        @Override
        public String id() {
            return "start-second";
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList("start-first");
        }

        @Override
        public void start(Settings settings, ApplicationContext context) {
            EVENTS.add("start-second");
            throw new IllegalStateException("start-failed");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            EVENTS.add("close-second");
        }
    }

    public static final class FailingRule implements FrameworkExtension {
        private boolean closed;

        @Override
        public String id() {
            return "failing-rule";
        }

        @Override
        public void register(Settings settings, ApplicationContext context) {
            context.addEnhancementRule(new EnhancementRule() {
                @Override
                public String id() {
                    return "fail-controller";
                }

                @Override
                public List<String> requires() {
                    return Collections.singletonList("controller-filter");
                }

                @Override
                public boolean matches(CtClass type, EnhancementContext enhancementContext) {
                    return true;
                }

                @Override
                public void apply(CtClass type, EnhancementContext enhancementContext) {
                    throw new EnhancementFailure(
                            EnhancementFailure.Category.ENHANCEMENT,
                            type.getName(),
                            id(),
                            "enhance",
                            "required enhancement failed",
                            null);
                }
            });
        }

        @Override
        public void start(Settings settings, ApplicationContext context) {
            EVENTS.add("failing-rule-start");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            EVENTS.add("failing-rule-close");
        }
    }
}
