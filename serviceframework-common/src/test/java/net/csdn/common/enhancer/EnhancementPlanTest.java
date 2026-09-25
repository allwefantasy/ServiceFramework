package net.csdn.common.enhancer;

import javassist.CtClass;
import javassist.CtMethod;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EnhancementPlanTest {

    @Test
    public void compilesStableOrderForOrmStyleDependencies() {
        EnhancementRule ormQuery = rule(EnhancementRuleIds.ORM_QUERY, 2, Collections.singletonList(EnhancementRuleIds.ENTITY_MAPPING), Collections.<String>emptyList());
        EnhancementRule association = rule(EnhancementRuleIds.ASSOCIATION, 3, Collections.singletonList(EnhancementRuleIds.ENTITY_MAPPING), Collections.<String>emptyList());
        EnhancementRule entity = rule(EnhancementRuleIds.ENTITY_MAPPING, 4, Collections.<String>emptyList(), Collections.<String>emptyList());
        EnhancementPlan plan = EnhancementPlan.compile(Arrays.asList(ormQuery, association, entity));
        assertEquals(Arrays.asList(
                EnhancementRuleIds.ENTITY_MAPPING,
                EnhancementRuleIds.ORM_QUERY,
                EnhancementRuleIds.ASSOCIATION), ids(plan));
    }

    @Test
    public void beforeConstraintMovesEarlierRuleForward() {
        EnhancementRule later = rule("later", 1, Collections.<String>emptyList(), Collections.<String>emptyList());
        EnhancementRule earlier = rule("earlier", 1, Collections.<String>emptyList(), Collections.singletonList("later"));
        EnhancementPlan plan = EnhancementPlan.compile(Arrays.asList(later, earlier));
        assertEquals(Arrays.asList("earlier", "later"), ids(plan));
    }

    @Test
    public void duplicateUnknownSelfAndCycleFailDuringCompile() {
        EnhancementRule one = rule("one", 1, Collections.<String>emptyList(), Collections.<String>emptyList());
        try {
            EnhancementPlan.compile(Arrays.asList(one, one));
            fail("duplicate");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
            assertEquals("one", failure.getRuleId());
            assertEquals("plan", failure.getPhase());
        }
        try {
            EnhancementPlan.compile(Collections.singletonList(
                    rule("lone", 1, Collections.singletonList("missing"), Collections.<String>emptyList())));
            fail("unknown");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.DEPENDENCY, failure.getCategory());
            assertTrue(failure.getMessage().contains("missing"));
        }
        try {
            EnhancementPlan.compile(Collections.singletonList(
                    rule("self", 1, Collections.singletonList("self"), Collections.<String>emptyList())));
            fail("self");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.DEPENDENCY, failure.getCategory());
        }
        EnhancementRule left = rule("left", 1, Collections.singletonList("right"), Collections.<String>emptyList());
        EnhancementRule right = rule("right", 1, Collections.singletonList("left"), Collections.<String>emptyList());
        try {
            EnhancementPlan.compile(Arrays.asList(left, right));
            fail("cycle");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.DEPENDENCY, failure.getCategory());
            assertTrue(failure.getMessage().contains("cyclic"));
        }
        try {
            EnhancementPlan.compile(Collections.singletonList(
                    rule("null-deps", 1, null, Collections.<String>emptyList())));
            fail("null requires");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
        }
    }

    @Test
    public void applyRecordsMatchesAndRejectsReplayAfterSuccessOrFailure() throws Exception {
        EnhancementRule skipped = rule("skipped", 9, Collections.<String>emptyList(), Collections.<String>emptyList(), false, false);
        EnhancementRule applied = rule("applied", 5, Collections.<String>emptyList(), Collections.<String>emptyList(), true, false);
        EnhancementPlan plan = EnhancementPlan.compile(Arrays.asList(skipped, applied));
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        try {
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Planned" + System.nanoTime());
            plan.apply(type, context);
            assertEquals(1, context.executions().size());
            assertEquals("applied", context.executions().get(0).ruleId());
            assertEquals(5, context.executions().get(0).version());
            assertEquals(type.getName(), context.executions().get(0).className());
            try {
                plan.apply(type, context);
                fail("replay");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("apply", failure.getPhase());
            }
        } finally {
            context.close();
        }

        EnhancementRule broken = rule("broken", 1, Collections.<String>emptyList(), Collections.<String>emptyList(), true, true);
        EnhancementPlan failing = EnhancementPlan.compile(Collections.singletonList(broken));
        EnhancementContext again = EnhancementContext.open(getClass().getClassLoader());
        try {
            CtClass type = again.makeClass("net.csdn.common.enhancer.fixture.Broken" + System.nanoTime());
            try {
                failing.apply(type, again);
                fail("failure");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.ENHANCEMENT, failure.getCategory());
                assertEquals("broken", failure.getRuleId());
                assertEquals(type.getName(), failure.getClassName());
                assertTrue(failure.getCause() instanceof IllegalStateException);
                assertEquals("boom", failure.getCause().getMessage());
            }
            try {
                failing.apply(type, again);
                fail("replay after failure");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
            }
        } finally {
            again.close();
        }
    }

    @Test
    public void executionRecordsLiveOnTheContextAndRejectSameContextReplay() throws Exception {
        String name = "net.csdn.common.enhancer.fixture.Shared" + System.nanoTime();
        EnhancementPlan plan = EnhancementPlan.compile(Collections.singletonList(
                rule("applied", 5, Collections.<String>emptyList(), Collections.<String>emptyList(), true, false)));
        EnhancementContext first = EnhancementContext.open(getClass().getClassLoader());
        try {
            CtClass type = first.makeClass(name);
            plan.apply(type, first);
            assertEquals(1, first.executions().size());
            EnhancementPlan recompiled = EnhancementPlan.compile(Collections.singletonList(
                    rule("applied", 5, Collections.<String>emptyList(), Collections.<String>emptyList(), true, false)));
            try {
                recompiled.apply(type, first);
                fail("same context replay");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals(name, failure.getClassName());
                assertEquals("apply", failure.getPhase());
            }
        } finally {
            first.close();
        }
        EnhancementContext second = EnhancementContext.open(getClass().getClassLoader());
        try {
            CtClass sameNamed = second.makeClass(name);
            plan.apply(sameNamed, second);
            assertEquals(1, second.executions().size());
            assertEquals(name, second.executions().get(0).className());
            assertEquals("applied", second.executions().get(0).ruleId());
        } finally {
            second.close();
        }
    }

    @Test
    public void partialFailureBlocksAnyRetryInTheSameContext() throws Exception {
        EnhancementPlan broken = EnhancementPlan.compile(Collections.singletonList(
                rule("broken", 1, Collections.<String>emptyList(), Collections.<String>emptyList(), true, true)));
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        try {
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Retry" + System.nanoTime());
            try {
                broken.apply(type, context);
                fail("failure");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.ENHANCEMENT, failure.getCategory());
            }
            EnhancementPlan fresh = EnhancementPlan.compile(Collections.singletonList(
                    rule("other", 1, Collections.<String>emptyList(), Collections.<String>emptyList(), false, false)));
            try {
                fresh.apply(type, context);
                fail("retry after failure");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals(type.getName(), failure.getClassName());
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void unmatchedRulesAreRecordedAsSkipsOnlyWhenDiagnosticsAreEnabled() throws Exception {
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "sf-skip-" + System.nanoTime());
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(dir);
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader(), diagnostics);
        try {
            EnhancementPlan plan = EnhancementPlan.compile(Arrays.asList(
                    rule("skipped", 1, Collections.<String>emptyList(), Collections.<String>emptyList(), false, false),
                    rule("applied", 1, Collections.<String>emptyList(), Collections.<String>emptyList(), true, false)));
            plan.apply(context.makeClass("net.csdn.common.enhancer.fixture.Skipped" + System.nanoTime()), context);
            boolean sawSkip = false;
            boolean sawApply = false;
            for (int i = 0; i < diagnostics.events().size(); i++) {
                EnhancementDiagnostics.Event event = diagnostics.events().get(i);
                if ("skip".equals(event.phase()) && "skipped".equals(event.ruleId())) {
                    sawSkip = true;
                }
                if ("apply".equals(event.phase()) && "applied".equals(event.ruleId())) {
                    sawApply = true;
                }
            }
            assertTrue("skip event", sawSkip);
            assertTrue("apply event", sawApply);
        } finally {
            context.close();
        }

        EnhancementDiagnostics disabled = EnhancementDiagnostics.disabled();
        EnhancementContext quiet = EnhancementContext.open(getClass().getClassLoader(), disabled);
        try {
            EnhancementPlan plan = EnhancementPlan.compile(Collections.singletonList(
                    rule("skipped", 1, Collections.<String>emptyList(), Collections.<String>emptyList(), false, false)));
            plan.apply(quiet.makeClass("net.csdn.common.enhancer.fixture.Quiet" + System.nanoTime()), quiet);
            assertTrue(disabled.events().isEmpty());
            assertEquals(0, disabled.hashComputations());
        } finally {
            quiet.close();
        }
    }

    @Test
    public void phaseTraceRecordsTheMeasuredApplyWithoutDiagnosticsWork() throws Exception {
        EnhancementPlan plan = EnhancementPlan.compile(Collections.singletonList(
                rule("applied", 5, Collections.<String>emptyList(), Collections.<String>emptyList(), true, false)));
        EnhancementContext quiet = EnhancementContext.open(getClass().getClassLoader());
        try {
            plan.apply(quiet.makeClass("net.csdn.common.enhancer.fixture.NoTrace" + System.nanoTime()), quiet);
            assertEquals(0, quiet.diagnostics().hashComputations());
            assertEquals(0, quiet.diagnostics().bytecodeReads());
        } finally {
            quiet.close();
        }
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        StartupPhaseTrace trace = new StartupPhaseTrace();
        context.setAttribute(StartupPhaseTrace.ATTRIBUTE, trace);
        try {
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Traced" + System.nanoTime());
            plan.apply(type, context);
            assertEquals(1, trace.samples().size());
            assertEquals("rule.applied", trace.samples().get(0).name);
            assertTrue(trace.samples().get(0).elapsedNanos >= 0L);
            assertEquals(false, trace.samples().get(0).nested);
            assertEquals(0, context.diagnostics().hashComputations());
            assertEquals(0, context.diagnostics().bytecodeReads());
            assertEquals(0, context.diagnostics().methodInspections());
            assertEquals(0, context.diagnostics().diskWrites());
        } finally {
            context.close();
        }
    }

    @Test
    public void registryRejectsDuplicateIdsAndCompiles() {
        EnhancementRules rules = new EnhancementRules();
        rules.register(rule(EnhancementRuleIds.MONGO_DOCUMENT, 1, Collections.<String>emptyList(), Collections.<String>emptyList()));
        rules.register(rule(EnhancementRuleIds.CONTROLLER_FILTER, 1, Collections.<String>emptyList(), Collections.<String>emptyList()));
        try {
            rules.register(rule(EnhancementRuleIds.MONGO_DOCUMENT, 1, Collections.<String>emptyList(), Collections.<String>emptyList()));
            fail("duplicate register");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
        }
        assertEquals(Arrays.asList(EnhancementRuleIds.MONGO_DOCUMENT, EnhancementRuleIds.CONTROLLER_FILTER), ids(rules.compile()));
    }

    @Test
    public void defaultVersionAndDependenciesAreEmpty() throws Exception {
        EnhancementRule rule = new EnhancementRule() {
            @Override
            public String id() {
                return "plain";
            }

            @Override
            public boolean matches(CtClass type, EnhancementContext context) {
                return true;
            }

            @Override
            public void apply(CtClass type, EnhancementContext context) {
                try {
                    type.addMethod(CtMethod.make("public void marked() {}", type));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        assertEquals(1, rule.version());
        assertTrue(rule.requires().isEmpty());
        assertTrue(rule.before().isEmpty());
        EnhancementPlan plan = EnhancementPlan.compile(Collections.singletonList(rule));
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        try {
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Plain" + System.nanoTime());
            assertEquals("matched", rule.applyReason(type, context));
            assertEquals("not matched", rule.skipReason(type, context));
            assertEquals(Collections.singletonList(type), rule.affectedClasses(type, context));
            plan.apply(type, context);
            assertEquals(1, context.executions().get(0).version());
            type.getDeclaredMethod("marked");
        } finally {
            context.close();
        }
    }

    private static List<String> ids(EnhancementPlan plan) {
        List<EnhancementRule> rules = plan.rules();
        String[] ids = new String[rules.size()];
        for (int i = 0; i < rules.size(); i++) {
            ids[i] = rules.get(i).id();
        }
        return Arrays.asList(ids);
    }

    private static EnhancementRule rule(String id, int version, List<String> requires, List<String> before) {
        return rule(id, version, requires, before, false, false);
    }

    private static EnhancementRule rule(
            final String id,
            final int version,
            final List<String> requires,
            final List<String> before,
            final boolean matched,
            final boolean fail) {
        return new EnhancementRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int version() {
                return version;
            }

            @Override
            public List<String> requires() {
                return requires;
            }

            @Override
            public List<String> before() {
                return before;
            }

            @Override
            public boolean matches(CtClass type, EnhancementContext context) {
                return matched;
            }

            @Override
            public void apply(CtClass type, EnhancementContext context) {
                if (fail) {
                    throw new IllegalStateException("boom");
                }
            }
        };
    }
}
