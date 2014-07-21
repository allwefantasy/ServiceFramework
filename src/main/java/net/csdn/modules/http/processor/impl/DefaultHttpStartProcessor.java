package net.csdn.modules.http.processor.impl;

import net.csdn.ServiceFramwork;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.hibernate.support.filter.CSDNStatFilterstat;
import net.csdn.modules.controller.API;
import net.csdn.modules.controller.QpsManager;
import net.csdn.modules.http.processor.HttpStartProcessor;
import net.csdn.modules.http.processor.ProcessInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class DefaultHttpStartProcessor implements HttpStartProcessor {
    boolean disableMysql = false;
    private CSLogger logger = Loggers.getLogger(DefaultHttpStartProcessor.class);

    @Override
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo) {
        disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        startORM(disableMysql);
        ServiceFramwork.injector.getInstance(API.class).qpsIncrement(processInfo.method);

        boolean qpsLimitEnable = settings.getAsBoolean("qpslimit.enable", false);
        if (qpsLimitEnable) {
            QpsManager qpsManager = ServiceFramwork.injector.getInstance(QpsManager.class);
            if (qpsManager.check(request.getRequestURI())) {
                logger.error("qps 限流");
                throw new RuntimeException("qps-overflow");
            }
        }
    }

    private void startORM(boolean _disableMysql) {
        if (!_disableMysql) {
            CSDNStatFilterstat.setSQLTIME(new AtomicLong(0l));
        }
    }
}
