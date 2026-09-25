package net.csdn.common.enhancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Explicit registration list. This is not a ServiceLoader plugin framework:
 * discovery that loads implementation classes can also load application classes
 * before enhancement. Callers register rules, then {@link #compile()}.
 */
public final class EnhancementRules {

    private final List<EnhancementRule> rules = new ArrayList<EnhancementRule>();

    public void register(EnhancementRule rule) {
        if (rule == null || rule.id() == null || rule.id().trim().length() == 0) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    rule == null ? null : rule.id(),
                    "register",
                    "rule id is required",
                    null);
        }
        for (int i = 0; i < rules.size(); i++) {
            if (rule.id().equals(rules.get(i).id())) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.CONFLICT,
                        null,
                        rule.id(),
                        "register",
                        "duplicate rule id",
                        null);
            }
        }
        rules.add(rule);
    }

    public List<EnhancementRule> rules() {
        return Collections.unmodifiableList(new ArrayList<EnhancementRule>(rules));
    }

    public EnhancementPlan compile() {
        return EnhancementPlan.compile(rules);
    }
}
