package net.csdn.common.enhancer;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ExceptionsAttribute;
import javassist.bytecode.MethodInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Stable topological plan. {@link #compile(List)} checks duplicate ids, unknown
 * dependencies and cycles before any class is modified. A plan is immutable and
 * reusable: it keeps no class, loader or execution state. The
 * {@link EnhancementContext} owns the per-class attempt record and the execution
 * report, so the same class name is rejected on a second apply inside one context
 * — including after a partial failure and from a differently compiled plan —
 * while another context may apply this same plan to a class of the same name.
 * The caller defines the class once, after apply returns.
 * <p>
 * When diagnostics are enabled, or an {@link EnhancementObserver} is registered,
 * each matching rule is asked for {@link EnhancementRule#affectedClasses} before
 * it runs. Original class bytes for that set are fixed before the rule mutates
 * anything. A rule that does not override the method only reports its own class.
 * Disabled diagnostics with no observer do not read bytecode or inspect methods.
 */
public final class EnhancementPlan {

    private final List<EnhancementRule> ordered;

    private EnhancementPlan(List<EnhancementRule> ordered) {
        this.ordered = Collections.unmodifiableList(new ArrayList<EnhancementRule>(ordered));
    }

    public static EnhancementPlan compile(List<EnhancementRule> rules) {
        if (rules == null) {
            throw planFailure(EnhancementFailure.Category.CONFIGURATION, null, "rules are required");
        }
        Map<String, EnhancementRule> byId = new LinkedHashMap<String, EnhancementRule>();
        Map<String, Integer> index = new HashMap<String, Integer>();
        for (int i = 0; i < rules.size(); i++) {
            EnhancementRule rule = rules.get(i);
            if (rule == null || rule.id() == null || rule.id().trim().length() == 0) {
                throw planFailure(
                        EnhancementFailure.Category.CONFIGURATION,
                        rule == null ? null : rule.id(),
                        "rule id is required");
            }
            if (byId.containsKey(rule.id())) {
                throw planFailure(EnhancementFailure.Category.CONFLICT, rule.id(), "duplicate rule id");
            }
            byId.put(rule.id(), rule);
            index.put(rule.id(), Integer.valueOf(i));
        }

        Map<String, List<String>> outgoing = new LinkedHashMap<String, List<String>>();
        Map<String, Integer> indegree = new HashMap<String, Integer>();
        Set<String> edges = new HashSet<String>();
        for (EnhancementRule rule : byId.values()) {
            outgoing.put(rule.id(), new ArrayList<String>());
            indegree.put(rule.id(), Integer.valueOf(0));
        }
        for (EnhancementRule rule : byId.values()) {
            addEdges(rule.id(), rule.requires(), true, byId, outgoing, indegree, edges);
            addEdges(rule.id(), rule.before(), false, byId, outgoing, indegree, edges);
        }

        PriorityQueue<String> ready = new PriorityQueue<String>(Math.max(1, byId.size()), new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return index.get(left).intValue() - index.get(right).intValue();
            }
        });
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue().intValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        List<EnhancementRule> ordered = new ArrayList<EnhancementRule>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            ordered.add(byId.get(id));
            List<String> next = outgoing.get(id);
            for (int i = 0; i < next.size(); i++) {
                String target = next.get(i);
                int degree = indegree.get(target).intValue() - 1;
                indegree.put(target, Integer.valueOf(degree));
                if (degree == 0) {
                    ready.add(target);
                }
            }
        }
        if (ordered.size() != byId.size()) {
            StringBuilder pending = new StringBuilder();
            for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
                if (entry.getValue().intValue() > 0) {
                    if (pending.length() > 0) {
                        pending.append(',');
                    }
                    pending.append(entry.getKey());
                }
            }
            throw planFailure(EnhancementFailure.Category.DEPENDENCY, null, "cyclic rule dependencies: " + pending);
        }
        return new EnhancementPlan(ordered);
    }

    public List<EnhancementRule> rules() {
        return ordered;
    }

    public void apply(CtClass type, EnhancementContext context) {
        if (type == null || context == null) {
            throw planFailure(EnhancementFailure.Category.CONFIGURATION, null, "class and context are required");
        }
        context.ensureOpen();
        String className = type.getName();
        context.beginEnhancement(className);
        EnhancementDiagnostics diagnostics = context.diagnostics();
        boolean diagnose = diagnostics.enabled();
        boolean observe = context.hasObservers();
        if (diagnose || observe) {
            captureOriginal(type, context, diagnostics, diagnose, observe);
        }
        for (int i = 0; i < ordered.size(); i++) {
            EnhancementRule rule = ordered.get(i);
            boolean match = matches(rule, type, context, className);
            if (!match) {
                if (diagnose) {
                    diagnostics.recordSkip(className, rule.id(), rule.version(), skipReason(rule, type, context, className));
                }
                continue;
            }
            List<CtClass> affected = Collections.singletonList(type);
            if (diagnose || observe) {
                affected = affectedClasses(rule, type, context, className);
                for (int j = 0; j < affected.size(); j++) {
                    captureOriginal(affected.get(j), context, diagnostics, diagnose, observe);
                }
            }
            Map<String, Map<String, Fingerprint>> before = diagnose
                    ? snapshotAll(affected, diagnostics)
                    : null;
            String reason = diagnose ? applyReason(rule, type, context, className) : null;
            long started = System.nanoTime();
            try {
                rule.apply(type, context);
            } catch (EnhancementFailure failure) {
                diagnostics.recordFailure(className, rule.id(), rule.version(), "apply", failure, System.nanoTime() - started);
                throw failure;
            } catch (Throwable thrown) {
                EnhancementFailure failure = ruleFailure(className, rule.id(), "rule apply failed", thrown);
                diagnostics.recordFailure(className, rule.id(), rule.version(), "apply", failure, System.nanoTime() - started);
                throw failure;
            }
            long elapsed = System.nanoTime() - started;
            context.recordExecution(className, rule.id(), rule.version());
            StartupPhaseTrace phases = StartupPhaseTrace.lookup(context);
            if (phases != null) {
                phases.record("rule." + rule.id(), elapsed);
            }
            if (diagnose) {
                for (int j = 0; j < affected.size(); j++) {
                    CtClass affectedType = affected.get(j);
                    Map<String, Fingerprint> previous = before.get(affectedType.getName());
                    if (previous == null) {
                        previous = Collections.emptyMap();
                    }
                    Map<String, Fingerprint> after = snapshot(affectedType, diagnostics);
                    List<EnhancementDiagnostics.MethodChange> changes = diff(
                            affectedType.getName(), previous, after, rule);
                    int delta = after.size() - previous.size();
                    long recorded = affectedType == type || className.equals(affectedType.getName()) ? elapsed : 0L;
                    diagnostics.recordApply(
                            affectedType.getName(),
                            rule.id(),
                            rule.version(),
                            delta,
                            recorded,
                            reason,
                            changes);
                    if (observe) {
                        context.notifyAfterRule(affectedType.getName(), rule.id(), rule.version(), changes);
                    }
                }
            } else if (observe) {
                for (int j = 0; j < affected.size(); j++) {
                    context.notifyAfterRule(
                            affected.get(j).getName(),
                            rule.id(),
                            rule.version(),
                            Collections.<EnhancementDiagnostics.MethodChange>emptyList());
                }
            }
        }
    }

    private static boolean matches(EnhancementRule rule, CtClass type, EnhancementContext context, String className) {
        try {
            return rule.matches(type, context);
        } catch (EnhancementFailure failure) {
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        } catch (Throwable thrown) {
            EnhancementFailure failure = ruleFailure(className, rule.id(), "rule match failed", thrown);
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        }
    }

    private static String skipReason(EnhancementRule rule, CtClass type, EnhancementContext context, String className) {
        try {
            String reason = rule.skipReason(type, context);
            if (reason == null || reason.trim().length() == 0) {
                return "not matched";
            }
            return reason;
        } catch (EnhancementFailure failure) {
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        } catch (Throwable thrown) {
            EnhancementFailure failure = ruleFailure(className, rule.id(), "skip reason failed", thrown);
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        }
    }

    private static String applyReason(EnhancementRule rule, CtClass type, EnhancementContext context, String className) {
        try {
            String reason = rule.applyReason(type, context);
            if (reason == null || reason.trim().length() == 0) {
                return "matched";
            }
            return reason;
        } catch (EnhancementFailure failure) {
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        } catch (Throwable thrown) {
            EnhancementFailure failure = ruleFailure(className, rule.id(), "apply reason failed", thrown);
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        }
    }

    private static List<CtClass> affectedClasses(
            EnhancementRule rule,
            CtClass type,
            EnhancementContext context,
            String className) {
        List<CtClass> declared;
        try {
            declared = rule.affectedClasses(type, context);
        } catch (EnhancementFailure failure) {
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        } catch (Throwable thrown) {
            EnhancementFailure failure = ruleFailure(className, rule.id(), "affected classes failed", thrown);
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        }
        if (declared == null) {
            EnhancementFailure failure = new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    className,
                    rule.id(),
                    "apply",
                    "affectedClasses returned null",
                    null);
            context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
            throw failure;
        }
        LinkedHashMap<String, CtClass> unique = new LinkedHashMap<String, CtClass>();
        unique.put(type.getName(), type);
        for (int i = 0; i < declared.size(); i++) {
            CtClass affected = declared.get(i);
            if (affected == null) {
                EnhancementFailure failure = new EnhancementFailure(
                        EnhancementFailure.Category.CONFIGURATION,
                        className,
                        rule.id(),
                        "apply",
                        "affectedClasses returned null",
                        null);
                context.diagnostics().recordFailure(className, rule.id(), rule.version(), "apply", failure, 0L);
                throw failure;
            }
            if (!unique.containsKey(affected.getName())) {
                unique.put(affected.getName(), affected);
            }
        }
        return new ArrayList<CtClass>(unique.values());
    }

    private static void captureOriginal(
            CtClass type,
            EnhancementContext context,
            EnhancementDiagnostics diagnostics,
            boolean diagnose,
            boolean observe) {
        String name = type.getName();
        if (context.originalCaptured(name)) {
            return;
        }
        byte[] original = readBytecode(type, name);
        if (diagnose) {
            diagnostics.noteBytecodeRead();
            diagnostics.noteOriginal(name, original);
        }
        context.markOriginalCaptured(name);
        if (observe) {
            try {
                context.notifyBeforeFirstMutation(name, original);
            } catch (EnhancementFailure failure) {
                diagnostics.recordFailure(name, null, "apply", failure, 0L);
                throw failure;
            } catch (Throwable thrown) {
                EnhancementFailure failure = new EnhancementFailure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        name,
                        null,
                        "apply",
                        "enhancement observer failed before mutation",
                        thrown instanceof Exception ? (Exception) thrown : new RuntimeException(thrown));
                diagnostics.recordFailure(name, null, "apply", failure, 0L);
                throw failure;
            }
        }
    }

    private static byte[] readBytecode(CtClass type, String className) {
        try {
            type.stopPruning(true);
            byte[] original = type.toBytecode();
            if (type.isFrozen()) {
                type.defrost();
            }
            return original;
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    className,
                    null,
                    "apply",
                    "cannot read original bytecode",
                    e);
        }
    }

    private static Map<String, Map<String, Fingerprint>> snapshotAll(
            List<CtClass> affected,
            EnhancementDiagnostics diagnostics) {
        Map<String, Map<String, Fingerprint>> snapshots = new LinkedHashMap<String, Map<String, Fingerprint>>();
        for (int i = 0; i < affected.size(); i++) {
            CtClass type = affected.get(i);
            snapshots.put(type.getName(), snapshot(type, diagnostics));
        }
        return snapshots;
    }

    private static Map<String, Fingerprint> snapshot(CtClass type, EnhancementDiagnostics diagnostics) {
        diagnostics.noteMethodInspection();
        Map<String, Fingerprint> fingerprints = new LinkedHashMap<String, Fingerprint>();
        CtMethod[] methods = type.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            CtMethod method = methods[i];
            String signature = method.getName() + method.getSignature();
            fingerprints.put(signature, fingerprint(method, signature));
        }
        return fingerprints;
    }

    private static Fingerprint fingerprint(CtMethod method, String signature) {
        MethodInfo info = method.getMethodInfo();
        CodeAttribute code = info.getCodeAttribute();
        byte[] codeBytes = code == null ? null : code.getCode();
        String codeHash = codeBytes == null ? "-" : EnhancementDiagnostics.sha256Hex(codeBytes);
        return new Fingerprint(signature, method.getModifiers(), codeHash, exceptions(info));
    }

    private static String exceptions(MethodInfo info) {
        ExceptionsAttribute attribute = info.getExceptionsAttribute();
        if (attribute == null) {
            return "-";
        }
        String[] names = attribute.getExceptions();
        if (names == null || names.length == 0) {
            return "-";
        }
        String[] copy = new String[names.length];
        System.arraycopy(names, 0, copy, 0, names.length);
        Arrays.sort(copy);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < copy.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(copy[i]);
        }
        return builder.toString();
    }

    private static List<EnhancementDiagnostics.MethodChange> diff(
            String className,
            Map<String, Fingerprint> before,
            Map<String, Fingerprint> after,
            EnhancementRule rule) {
        List<EnhancementDiagnostics.MethodChange> changes = new ArrayList<EnhancementDiagnostics.MethodChange>();
        Set<String> signatures = new HashSet<String>();
        signatures.addAll(before.keySet());
        signatures.addAll(after.keySet());
        List<String> ordered = new ArrayList<String>(signatures);
        Collections.sort(ordered);
        for (int i = 0; i < ordered.size(); i++) {
            String signature = ordered.get(i);
            Fingerprint previous = before.get(signature);
            Fingerprint next = after.get(signature);
            String kind = null;
            if (previous == null && next != null) {
                kind = EnhancementDiagnostics.MethodChange.ADDED;
            } else if (previous != null && next == null) {
                kind = EnhancementDiagnostics.MethodChange.REMOVED;
            } else if (previous != null && !previous.same(next)) {
                kind = EnhancementDiagnostics.MethodChange.REWRITTEN;
            }
            if (kind != null) {
                changes.add(new EnhancementDiagnostics.MethodChange(
                        className, kind, signature, rule.id(), rule.version()));
            }
        }
        Collections.sort(changes, new Comparator<EnhancementDiagnostics.MethodChange>() {
            @Override
            public int compare(EnhancementDiagnostics.MethodChange left, EnhancementDiagnostics.MethodChange right) {
                int kind = kindOrder(left.change()) - kindOrder(right.change());
                if (kind != 0) {
                    return kind;
                }
                return left.signature().compareTo(right.signature());
            }
        });
        return changes;
    }

    private static int kindOrder(String kind) {
        if (EnhancementDiagnostics.MethodChange.ADDED.equals(kind)) {
            return 0;
        }
        if (EnhancementDiagnostics.MethodChange.REWRITTEN.equals(kind)) {
            return 1;
        }
        return 2;
    }

    private static EnhancementFailure ruleFailure(String className, String ruleId, String detail, Throwable thrown) {
        return new EnhancementFailure(
                EnhancementFailure.Category.ENHANCEMENT,
                className,
                ruleId,
                "apply",
                detail,
                thrown instanceof Exception ? (Exception) thrown : new RuntimeException(thrown));
    }

    private static final class Fingerprint {
        private final String signature;
        private final int modifiers;
        private final String codeHash;
        private final String exceptions;

        private Fingerprint(String signature, int modifiers, String codeHash, String exceptions) {
            this.signature = signature;
            this.modifiers = modifiers;
            this.codeHash = codeHash;
            this.exceptions = exceptions;
        }

        private boolean same(Fingerprint other) {
            return modifiers == other.modifiers
                    && codeHash.equals(other.codeHash)
                    && exceptions.equals(other.exceptions)
                    && signature.equals(other.signature);
        }
    }

    private static void addEdges(
            String id,
            List<String> names,
            boolean requires,
            Map<String, EnhancementRule> byId,
            Map<String, List<String>> outgoing,
            Map<String, Integer> indegree,
            Set<String> edges) {
        if (names == null) {
            throw planFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    id,
                    requires ? "requires() returned null" : "before() returned null");
        }
        for (int i = 0; i < names.size(); i++) {
            String other = names.get(i);
            if (other == null || !byId.containsKey(other)) {
                throw planFailure(
                        EnhancementFailure.Category.DEPENDENCY,
                        id,
                        "unknown rule dependency: " + other);
            }
            if (other.equals(id)) {
                throw planFailure(EnhancementFailure.Category.DEPENDENCY, id, "rule depends on itself");
            }
            String from = requires ? other : id;
            String to = requires ? id : other;
            String edge = from + "->" + to;
            if (!edges.add(edge)) {
                continue;
            }
            outgoing.get(from).add(to);
            indegree.put(to, Integer.valueOf(indegree.get(to).intValue() + 1));
        }
    }

    private static EnhancementFailure planFailure(EnhancementFailure.Category category, String ruleId, String detail) {
        return new EnhancementFailure(category, null, ruleId, "plan", detail, null);
    }

    public static final class Execution {
        private final String className;
        private final String ruleId;
        private final int version;

        public Execution(String className, String ruleId, int version) {
            this.className = className;
            this.ruleId = ruleId;
            this.version = version;
        }

        public String className() {
            return className;
        }

        public String ruleId() {
            return ruleId;
        }

        public int version() {
            return version;
        }
    }
}
