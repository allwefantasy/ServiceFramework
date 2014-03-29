package net.csdn.modules.http.processor.impl;

import net.csdn.ServiceFramwork;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.hibernate.support.filter.CSDNStatFilterstat;
import net.csdn.jpa.JPA;
import net.csdn.modules.http.processor.HttpFinishProcessor;
import net.csdn.modules.http.processor.ProcessInfo;
import net.csdn.trace.Trace;

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
        endORM(disableMySql);
        closeTx(settings);
        systemLog(processInfo.startTime, request, settings, processInfo);
        Trace.clean();
    }

    private void endORM(boolean _disableMysql) {
        if (!_disableMysql) {
            CSDNStatFilterstat.removeSQLTIME();
        }
    }


    private void closeTx(Settings settings) {
        boolean disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        if (!disableMysql) {
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
            logger.info("Completed " + processInfo.status + " in " + (endTime - startTime) + "ms (ActiveORM: " + (disableMysql ? 0 : CSDNStatFilterstat.SQLTIME().get()) + "ms)");
            logger.info(httpServletRequest.getMethod() +
                    " " + httpServletRequest.getRequestURI() + (isNull(url) ? "" : ("?" + url)));
            logger.info("\n\n\n\n");
        }
    }
}
