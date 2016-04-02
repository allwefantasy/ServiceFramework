package net.csdn.modules.http.processor.impl;

import net.csdn.ServiceFramwork;
import net.csdn.annotation.NoTransaction;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.hibernate.support.filter.CSDNStatFilterstat;
import net.csdn.jpa.JPA;
import net.csdn.modules.controller.API;
import net.csdn.modules.http.processor.HttpFinishProcessor;
import net.csdn.modules.http.processor.ProcessInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static net.csdn.common.collections.WowCollections.isNull;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class DefaultHttpFinishProcessor implements HttpFinishProcessor {

    private CSLogger logger = Loggers.getLogger(DefaultHttpFinishProcessor.class);

    @Override
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo) {
        boolean disableMySql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        systemLog(processInfo.startTime, request, settings, processInfo);
        endORM(disableMySql);
        closeTx(settings, processInfo);
        API api = ServiceFramwork.injector.getInstance(API.class);
        api.statusIncrement(processInfo.method, processInfo.status);
        api.averageTimeIncrement(processInfo.method, System.currentTimeMillis() - processInfo.startTime);
    }

    private void endORM(boolean _disableMysql) {
        if (!_disableMysql) {
            CSDNStatFilterstat.removeSQLTIME();
        }
    }


    private void closeTx(Settings settings, ProcessInfo processInfo) {
        boolean disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        if (!disableMysql && processInfo.method != null && processInfo.method.getAnnotation(NoTransaction.class) == null) {
            try {
                JPA.getJPAConfig().getJPAContext().closeTx(false);
            } catch (Exception e2) {
                //ignore
            }
        }
    }

    private void systemLog(long startTime, HttpServletRequest httpServletRequest, Settings settings, ProcessInfo processInfo) {
        boolean disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        boolean logEnable = settings.getAsBoolean("application.log.enable", true);
        if (logEnable) {
            long endTime = System.currentTimeMillis();
            String url = httpServletRequest.getQueryString();
            String activeOrmTime = disableMysql ? "" : "(ActiveORM: " + CSDNStatFilterstat.SQLTIME().get() + "ms)";
            String completed = "Completed " + processInfo.status + " in " + (endTime - startTime) + "ms " + activeOrmTime;
            logger.info(completed + "\t" + httpServletRequest.getMethod() +
                    " " + httpServletRequest.getRequestURI() + (isNull(url) ? "" : ("?" + url)) + "\n\n\n");
        }
    }
}
