package net.csdn.jpa.enhancer;

import javassist.CtClass;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRules;
import net.csdn.jpa.JPA;
import net.csdn.jpa.OrmSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one enhancement plan and applies it parent-first. Rules do not define classes.
 */
public final class OrmEnhancer {

    private OrmEnhancer() {
    }

    public static List<ModelClass> enhance(List<CtClass> types) {
        OrmSession session = OrmSession.current();
        EnhancementContext context = session.context();
        ModelClass.Tree tree = ModelClass.buildTree(types);
        session.replaceModelTree(tree.roots(), tree.all());
        List<ModelClass> order = ModelClass.parentFirst(tree.roots());
        EnhancementRules rules = new EnhancementRules();
        rules.register(new EntityMappingRule());
        rules.register(new OrmQueryRule());
        rules.register(new AssociationRule());
        List<EnhancementRule> extras = session.configuration().enhancementRules();
        for (int i = 0; i < extras.size(); i++) {
            rules.register(extras.get(i));
        }
        EnhancementPlan plan = rules.compile();
        for (int i = 0; i < order.size(); i++) {
            CtClass type = order.get(i).originClass;
            try {
                plan.apply(type, context);
            } catch (EnhancementFailure failure) {
                throw failure;
            } catch (RuntimeException e) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        type.getName(),
                        null,
                        "apply",
                        "enhancement plan failed",
                        e);
            }
        }
        rejectDuplicateEntityNames(tree.all());
        return order;
    }

    static void rejectDuplicateEntityNames(List<ModelClass> models) {
        Map<String, String> owners = new LinkedHashMap<String, String>();
        for (int i = 0; i < models.size(); i++) {
            CtClass type = models.get(i).originClass;
            String entityName;
            try {
                if (!type.hasAnnotation(javax.persistence.Entity.class)) {
                    continue;
                }
                entityName = ModelNames.readEntityName(type);
            } catch (Exception e) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        type.getName(),
                        "entity-mapping",
                        "enhance",
                        "entity name could not be read",
                        e);
            }
            if (entityName == null || entityName.length() == 0) {
                entityName = type.getName();
            }
            String previous = owners.put(entityName, type.getName());
            if (previous != null) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.CONFLICT,
                        type.getName(),
                        "entity-mapping",
                        "enhance",
                        "entity name " + entityName + " is already used by " + previous,
                        null);
            }
        }
    }

    public static List<CtClass> modelTypes(List<CtClass> types) {
        List<CtClass> models = new ArrayList<CtClass>();
        for (int i = 0; i < types.size(); i++) {
            if (ModelClass.isModelSubclass(types.get(i))) {
                models.add(types.get(i));
            }
        }
        return models;
    }
}
