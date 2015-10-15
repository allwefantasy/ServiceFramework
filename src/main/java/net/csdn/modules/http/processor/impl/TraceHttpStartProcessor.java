package net.csdn.modules.http.processor.impl;

import net.csdn.common.settings.Settings;
import net.csdn.modules.http.processor.HttpStartProcessor;
import net.csdn.modules.http.processor.ProcessInfo;
import net.csdn.trace.RemoteTraceElementKey;
import net.csdn.trace.Trace;
import net.csdn.trace.TraceContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 10/15/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class TraceHttpStartProcessor implements HttpStartProcessor {
    @Override
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo) {
        String traceId = request.getParameter(RemoteTraceElementKey.TRACEID());
        TraceContext traceContext = null;
        if (traceId == null) {
            traceContext = TraceContext.createRemoteContext();
        } else {
            traceContext = TraceContext.parseRemoteContext(request.getParameterMap());
        }
        if (traceContext != null) {
            Trace.set(traceContext);
        }


    }
}
