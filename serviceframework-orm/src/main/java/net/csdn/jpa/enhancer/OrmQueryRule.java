package net.csdn.jpa.enhancer;

import javassist.CtClass;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.jpa.JPA;
import net.csdn.jpa.OrmSession;

import java.util.Collections;
import java.util.List;

public final class OrmQueryRule implements EnhancementRule {

    @Override
    public String id() {
        return EnhancementRuleIds.ORM_QUERY;
    }

    @Override
    public List<String> requires() {
        return Collections.singletonList(EnhancementRuleIds.ENTITY_MAPPING);
    }

    @Override
    public boolean matches(CtClass type, EnhancementContext context) {
        ModelClass modelClass = model(type);
        return modelClass != null && modelClass.isLeafNode();
    }

    @Override
    public String applyReason(CtClass type, EnhancementContext context) {
        return "leaf model receives query methods";
    }

    @Override
    public String skipReason(CtClass type, EnhancementContext context) {
        ModelClass modelClass = model(type);
        if (modelClass == null) {
            return "not a model";
        }
        return "query methods are added only on leaf models";
    }

    @Override
    public void apply(CtClass type, EnhancementContext context) {
        ModelClass modelClass = model(type);
        if (modelClass == null) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    type.getName(),
                    id(),
                    "apply",
                    "model metadata is missing",
                    null);
        }
        try {
            new ClassMethodEnhancer(JPA.settings()).enhance(Collections.singletonList(modelClass));
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    type.getName(),
                    id(),
                    "apply",
                    "query methods were not added",
                    e);
        }
    }

    private static ModelClass model(CtClass type) {
        OrmSession session = OrmSession.currentOrNull();
        if (session == null || type == null) {
            return null;
        }
        return session.modelClass(type.getName());
    }
}
