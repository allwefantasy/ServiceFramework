package net.csdn.modules.http.processor.impl;

import net.csdn.common.collections.WowCollections;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.processor.HttpStartProcessor;
import net.csdn.modules.http.processor.ProcessInfo;
import net.csdn.trace.RemoteTraceElementKey;
import net.csdn.trace.Trace;
import net.csdn.trace.TraceContext;
import net.csdn.trace.VisitType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 10/15/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class TraceHttpStartProcessor implements HttpStartProcessor {
    private CSLogger logger = Loggers.getLogger(TraceHttpStartProcessor.class);

    @Override
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo) {
        String traceId = request.getParameter(RemoteTraceElementKey.TRACEID());
        TraceContext traceContext = null;
        String hostName = request.getServerName();
        int port = request.getServerPort();
        String scheme = request.getScheme();
        String queryStr = request.getQueryString();
        String url = scheme + "://" + hostName + ":" + port + request.getRequestURI() + (WowCollections.isNull(queryStr) ? "" : ("?" + queryStr));

        if (traceId == null) {
            traceContext = TraceContext.createRemoteContext();
            traceContext.openDoor("0.0", url, VisitType.HTTP_SERVICE());
            traceContext.log(logger, traceContext.remoteTraceElement());
        } else {
            traceContext = TraceContext.parseRemoteContext(request.getParameterMap());
            traceContext.configRemoteTraceElement(traceContext.newRemoteTraceElement(false, traceContext.currentRpcId(), url, VisitType.HTTP_SERVICE()));
            traceContext.log(logger, traceContext.remoteTraceElement());
        }
        if (traceContext != null) {
            Trace.set(traceContext);
        }


    }
}
