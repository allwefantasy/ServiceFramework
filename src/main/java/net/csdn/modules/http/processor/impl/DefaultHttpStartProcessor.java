package net.csdn.modules.http.processor.impl;

import net.csdn.ServiceFramwork;
import net.csdn.common.settings.Settings;
import net.csdn.hibernate.support.filter.CSDNStatFilterstat;
import net.csdn.modules.controller.API;
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

    @Override
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo) {
        String traceId = request.getParameter("traceId");
        String traceParent = request.getParameter("traceParent");
        String traceSpan = request.getParameter("traceSpan");

//        Trace.set(TraceContext.initial(
//                settings.get("application.name", "default_system"),
//                request.getRequestURI().toString(),
//                request.getContentLength(),
//                traceId,
//                traceParent == null ? -1 : Integer.parseInt(traceParent),
//                traceSpan == null ? -1 : Integer.parseInt(traceSpan)
//        )
//        );
        disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        startORM(disableMysql);
        ServiceFramwork.injector.getInstance(API.class).qpsIncrement(processInfo.method);
    }

    private void startORM(boolean _disableMysql) {
        if (!_disableMysql) {
            CSDNStatFilterstat.setSQLTIME(new AtomicLong(0l));
        }
    }
}
