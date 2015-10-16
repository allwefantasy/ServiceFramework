package net.csdn.modules.http.processor.impl;

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
        TraceContext traceContext = Trace.get();
        if (traceContext != null) {
            traceContext.log(logger, traceContext.newRemoteTraceElement(false, traceContext.rpcId(), VisitType.HTTP_SERVICE()));
        }
        Trace.clean();
    }
}
