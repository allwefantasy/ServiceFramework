package net.csdn.modules.http.processor.impl;

import net.csdn.common.collections.WowCollections;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.processor.HttpFinishProcessor;
import net.csdn.modules.http.processor.ProcessInfo;
import net.csdn.trace.Trace;
import net.csdn.trace.TraceContext;
import net.csdn.trace.VisitType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 10/15/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class TraceHttpFinishProcessor implements HttpFinishProcessor {
    private CSLogger logger = Loggers.getLogger(TraceHttpFinishProcessor.class);

    @Override
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo) {
        try {
            TraceContext traceContext = Trace.get();
            if (traceContext != null) {
                String hostName = request.getServerName();
                int port = request.getServerPort();
                String scheme = request.getScheme();
                String queryStr = request.getQueryString();
                String url = scheme + "://" + hostName + ":" + port + request.getRequestURI() + (WowCollections.isNull(queryStr) ? "" : ("?" + queryStr));
                traceContext.log(logger, traceContext.
                        newRemoteTraceElement(false, traceContext.rpcId(), url, VisitType.HTTP_SERVICE()));
            }
            Trace.clean();
        } catch (Exception e) {
            logger.info("trace clean error", e);
        }

    }
}
