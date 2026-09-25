package net.csdn.bootstrap.loader.impl;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.extension.OrmFrameworkExtension;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;

import java.util.List;

/**
 * Compatibility entry for the ORM lifecycle. Scanning and definition stay in
 * {@link JPA#configure(JPA.CSDNORMConfiguration, net.csdn.common.enhancer.EnhancementContext)}.
 * This loader does not call {@code toClass} and does not swallow failures.
 */
public class ModelLoader implements Loader {
    @Override
    public void load(Settings settings) throws Exception {
        ApplicationContext application = ApplicationContext.require();
        JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration(
                application.mode().name(),
                settings,
                application.marker());
        JPA.configure(configuration, application.enhancementContext());
        if (!OrmFrameworkExtension.mysqlDisabled(settings, application)) {
            markIfRuleRan(application, EnhancementRuleIds.ENTITY_MAPPING);
        }
    }

    public static void markIfRuleRan(ApplicationContext application, String ruleId) {
        List<EnhancementPlan.Execution> executions = application.enhancementContext().executions();
        for (int i = 0; i < executions.size(); i++) {
            if (ruleId.equals(executions.get(i).ruleId())) {
                application.markClassesDefined();
                return;
            }
        }
    }
}
