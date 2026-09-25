package net.csdn.jpa.enhancer;

import javassist.CtClass;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.jpa.JPA;
import net.csdn.jpa.OrmSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Collection entity mapping runs once, on the first matched target. Later targets
 * in the same tree are no-ops. A target outside that tree, or a second collection
 * pass, fails before any class is defined.
 * <p>
 * The first pass edits every model in the tree, so diagnostics must freeze those
 * original hashes before {@link #apply}. Later passes report only the entry class.
 */
public final class EntityMappingRule implements EnhancementRule {

    @Override
    public String id() {
        return EnhancementRuleIds.ENTITY_MAPPING;
    }

    @Override
    public boolean matches(CtClass type, EnhancementContext context) {
        return ModelClass.isModelSubclass(type);
    }

    @Override
    public List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
        OrmSession session = OrmSession.current();
        if (session.entityMappingDone()) {
            return Collections.singletonList(type);
        }
        LinkedHashMap<String, CtClass> types = new LinkedHashMap<String, CtClass>();
        types.put(type.getName(), type);
        List<ModelClass> roots = session.roots();
        for (int i = 0; i < roots.size(); i++) {
            List<ModelClass> hierarchy = roots.get(i).hierarchy();
            for (int j = 0; j < hierarchy.size(); j++) {
                CtClass origin = hierarchy.get(j).originClass;
                types.put(origin.getName(), origin);
            }
        }
        return new ArrayList<CtClass>(types.values());
    }

    @Override
    public String applyReason(CtClass type, EnhancementContext context) {
        OrmSession session = OrmSession.currentOrNull();
        if (session != null && session.entityMappingDone()) {
            return "entity mapping already includes this class";
        }
        return "maps the model tree from this class";
    }

    @Override
    public String skipReason(CtClass type, EnhancementContext context) {
        return "not a Model subclass";
    }

    @Override
    public void apply(CtClass type, EnhancementContext context) {
        OrmSession session = OrmSession.current();
        if (session.entityMappingDone()) {
            if (!session.knowsMappedType(type.getName())) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.CONFLICT,
                        type.getName(),
                        id(),
                        "apply",
                        "entity-mapping already ran and does not include this class",
                        null);
            }
            return;
        }
        try {
            new EntityEnhancer(JPA.settings()).enhance(session.roots());
            session.markEntityMappingDone();
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    type.getName(),
                    id(),
                    "apply",
                    "entity mapping failed",
                    e);
        }
    }

    @Override
    public List<String> requires() {
        return Collections.emptyList();
    }
}
