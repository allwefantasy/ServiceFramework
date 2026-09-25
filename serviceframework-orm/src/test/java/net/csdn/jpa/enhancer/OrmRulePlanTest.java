package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import net.csdn.jpa.OrmSession;
import net.csdn.jpa.model.Model;
import org.junit.Test;

import javax.persistence.Entity;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.csdn.common.collections.WowCollections.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OrmRulePlanTest {

    @Test
    public void runsMappingBeforeQueryAndAssociationAndDoesNotDefineClasses() throws Exception {
        EnhancementContext context = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            try (EnhancementContext.Scope ignored = context.activate()) {
                bindDisabled(context, "net.csdn.jpa.enhancer.plan");
                CtClass model = context.makeClass("net.csdn.jpa.enhancer.plan.Item");
                model.setSuperclass(context.get(Model.class.getName()));
                model.addField(CtField.make("private java.lang.String name;", model));
                OrmEnhancer.enhance(Collections.singletonList(model));
                List<EnhancementPlan.Execution> executions = context.executions();
                assertTrue(executions.size() >= 3);
                assertEquals(EnhancementRuleIds.ENTITY_MAPPING, executions.get(0).ruleId());
                assertEquals(EnhancementRuleIds.ORM_QUERY, executions.get(1).ruleId());
                assertEquals(EnhancementRuleIds.ASSOCIATION, executions.get(2).ruleId());
                assertTrue(model.getDeclaredMethods().length > 0);
            }
            try {
                Class.forName("net.csdn.jpa.enhancer.plan.Item");
                fail("plan defined a JVM class");
            } catch (ClassNotFoundException expected) {
                assertEquals("net.csdn.jpa.enhancer.plan.Item", expected.getMessage());
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void duplicateRuleAndEntityNameFailBeforeDefine() throws Exception {
        EnhancementContext context = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            try (EnhancementContext.Scope ignored = context.activate()) {
                JPA.CSDNORMConfiguration configuration = bindDisabled(context, "net.csdn.jpa.enhancer.plan2");
                configuration.addEnhancementRule(new EnhancementRule() {
                    @Override
                    public String id() {
                        return EnhancementRuleIds.ENTITY_MAPPING;
                    }

                    @Override
                    public boolean matches(CtClass type, EnhancementContext enhancementContext) {
                        return false;
                    }

                    @Override
                    public void apply(CtClass type, EnhancementContext enhancementContext) {
                        fail("duplicate rule was applied");
                    }
                });
                CtClass model = context.makeClass("net.csdn.jpa.enhancer.plan2.Item");
                model.setSuperclass(context.get(Model.class.getName()));
                try {
                    OrmEnhancer.enhance(Collections.singletonList(model));
                    fail("duplicate rule id was accepted");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                    assertEquals(EnhancementRuleIds.ENTITY_MAPPING, failure.getRuleId());
                }
            }
        } finally {
            context.close();
        }

        EnhancementContext second = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            try (EnhancementContext.Scope ignored = second.activate()) {
                bindDisabled(second, "net.csdn.jpa.enhancer.plan3");
                CtClass left = named(second, "net.csdn.jpa.enhancer.plan3.Left");
                CtClass right = named(second, "net.csdn.jpa.enhancer.plan3.Right");
                try {
                    OrmEnhancer.enhance(Arrays.asList(left, right));
                    fail("duplicate entity name reached define");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                    assertTrue(failure.getMessage().contains("Same"));
                }
            }
            try {
                Class.forName("net.csdn.jpa.enhancer.plan3.Left");
                fail("conflicting entity was defined");
            } catch (ClassNotFoundException expected) {
                assertEquals("net.csdn.jpa.enhancer.plan3.Left", expected.getMessage());
            }
        } finally {
            second.close();
        }
    }

    @Test
    public void reapplyingAClassInTheSameContextConflicts() throws Exception {
        EnhancementContext context = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            try (EnhancementContext.Scope ignored = context.activate()) {
                bindDisabled(context, "net.csdn.jpa.enhancer.plan4");
                CtClass model = context.makeClass("net.csdn.jpa.enhancer.plan4.Item");
                model.setSuperclass(context.get(Model.class.getName()));
                OrmEnhancer.enhance(Collections.singletonList(model));
                try {
                    OrmEnhancer.enhance(Collections.singletonList(model));
                    fail("second apply was accepted");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                    assertEquals("apply", failure.getPhase());
                }
            }
        } finally {
            context.close();
        }
    }

    private static JPA.CSDNORMConfiguration bindDisabled(EnhancementContext context, String modelPackage) {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "true")
                .put("development.datasources.mysql.host", "127.0.0.1")
                .put("development.datasources.mysql.port", "1")
                .put("development.datasources.mysql.database", "sf_compat")
                .put("development.datasources.mysql.username", "unused")
                .put("development.datasources.mysql.password", "unused")
                .put("application.model", modelPackage)
                .build();
        JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration("development", settings, JPA.class);
        OrmSession session = OrmSession.attach(context);
        session.bindConfiguration(configuration);
        configuration.buildDefaultDBInfo();
        session.setDbInfo(configuration.getDbInfo());
        return configuration;
    }

    private static CtClass named(EnhancementContext context, String binaryName) throws Exception {
        CtClass type = context.makeClass(binaryName);
        type.setSuperclass(context.get(Model.class.getName()));
        javassist.bytecode.ConstPool constPool = type.getClassFile().getConstPool();
        EnhancerHelper.createAnnotation(type, Entity.class, map(
                "name", new javassist.bytecode.annotation.StringMemberValue("Same", constPool)
        ));
        return type;
    }
}
