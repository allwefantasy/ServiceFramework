package net.csdn.jpa.enhancer;

import javassist.CtClass;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.jpa.JPA;
import net.csdn.jpa.OrmSession;

import javax.persistence.Inheritance;
import javax.persistence.MappedSuperclass;
import java.util.Collections;
import java.util.List;

public final class AssociationRule implements EnhancementRule {

    @Override
    public String id() {
        return EnhancementRuleIds.ASSOCIATION;
    }

    @Override
    public List<String> requires() {
        return Collections.singletonList(EnhancementRuleIds.ENTITY_MAPPING);
    }

    @Override
    public boolean matches(CtClass type, EnhancementContext context) {
        ModelClass modelClass = model(type);
        if (modelClass == null) {
            return false;
        }
        if (modelClass.isLeafNode()) {
            return true;
        }
        return modelClass.parent() == null
                && (type.hasAnnotation(MappedSuperclass.class) || type.hasAnnotation(Inheritance.class));
    }

    @Override
    public String applyReason(CtClass type, EnhancementContext context) {
        return "associations are added on this model";
    }

    @Override
    public String skipReason(CtClass type, EnhancementContext context) {
        return "associations are not added to this class";
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
            new AssociationEnhancer(JPA.settings()).enhance(Collections.singletonList(modelClass));
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    type.getName(),
                    id(),
                    "apply",
                    "associations were not added",
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
