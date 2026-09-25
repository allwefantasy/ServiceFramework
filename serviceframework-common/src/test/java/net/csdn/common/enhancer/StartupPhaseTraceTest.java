package net.csdn.common.enhancer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StartupPhaseTraceTest {

    @Test
    public void absentAttributeDoesNotOpenAClock() {
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        try {
            assertEquals(null, StartupPhaseTrace.lookup(context));
            StartupPhaseTrace.Frame frame = StartupPhaseTrace.open(context, "scan.service");
            frame.close();
            frame.close();
            assertSame(null, context.getAttribute(StartupPhaseTrace.ATTRIBUTE));
        } finally {
            context.close();
        }
        StartupPhaseTrace.Frame inactive = StartupPhaseTrace.open(null, "scan.service");
        inactive.close();
    }

    @Test
    public void nestedSampleIsExcludedFromTheExclusiveSum() throws Exception {
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        StartupPhaseTrace trace = new StartupPhaseTrace();
        context.setAttribute(StartupPhaseTrace.ATTRIBUTE, trace);
        try {
            StartupPhaseTrace.Frame outer = StartupPhaseTrace.open(context, "guice");
            try {
                Thread.sleep(1L);
                StartupPhaseTrace.Frame inner = StartupPhaseTrace.open(context, "jpa.emf");
                try {
                    Thread.sleep(1L);
                } finally {
                    inner.close();
                }
                trace.record("rule.entity-mapping", 25L);
            } finally {
                outer.close();
            }
            trace.record("define", 40L);
        } finally {
            context.close();
        }
        assertEquals(4, trace.samples().size());
        assertTrue(trace.samples().get(0).nested);
        assertEquals("jpa.emf", trace.samples().get(0).name);
        assertTrue(trace.samples().get(1).nested);
        assertEquals("rule.entity-mapping", trace.samples().get(1).name);
        assertFalse(trace.samples().get(2).nested);
        assertEquals("guice", trace.samples().get(2).name);
        assertFalse(trace.samples().get(3).nested);
        assertEquals(trace.samples().get(2).elapsedNanos + 40L, trace.exclusiveNanos());
        assertTrue(trace.exclusiveNanos() < trace.samples().get(0).elapsedNanos
                + trace.samples().get(1).elapsedNanos
                + trace.samples().get(2).elapsedNanos
                + trace.samples().get(3).elapsedNanos);
    }
}
