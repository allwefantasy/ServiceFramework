package net.csdn.common.enhancer;

import java.util.List;

/**
 * Observes one context. Register it before {@link EnhancementPlan#apply} so the
 * original bytes are the bytes from before the first mutation.
 * <p>
 * The byte array is only valid for the duration of
 * {@link #beforeFirstMutation(String, byte[])}. Copy it if it must be kept.
 * Implementations must not retain database passwords, raw configuration or other
 * secrets copied out of a constant pool.
 */
public interface EnhancementObserver {

    void beforeFirstMutation(String className, byte[] originalBytecode);

    void afterRule(String className, String ruleId, int ruleVersion, List<EnhancementDiagnostics.MethodChange> changes);
}
