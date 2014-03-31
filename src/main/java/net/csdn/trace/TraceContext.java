package net.csdn.trace;

import net.csdn.ServiceFramwork;
import net.csdn.annotation.FDesc;
import net.csdn.modules.log.SystemLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static net.csdn.common.collections.WowCollections.map;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class TraceContext {
    private String traceId;
    private String traceAppName;
    private String traceAPI;
    private String traceParent;
    private String traceSpan = "0";
    private AtomicInteger traceSpanInc = new AtomicInteger(-1);


    private long requestLen = 0;
    private long responseLen = 0;


    public TraceContext enterMethod(String methodName) {
        log("enter", methodName);
        return this;
    }


    public TraceContext exitMethod(String methodName) {
        log("exit", methodName);
        return this;
    }

    public static Map sendHttpTtraceContext(TraceContext context) {
        return map(
                "traceSpan", context.traceSpan + "." + context.traceSpanInc.incrementAndGet(),
                "traceId", context.traceId);

    }

    public static Trace receiveHttpTrateContext(Map items) {
        return null;
    }

    private void log(String enterOrExit, String methodName) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(traceId + " " + traceAPI + " " + traceParent + " " + traceSpan + " " + traceAppName + " " + methodName);
        buffer.append(" " + enterOrExit);
        buffer.append(" " + System.currentTimeMillis());
        ServiceFramwork.injector.getInstance(SystemLogger.class).traceLogger().info(buffer.toString());
    }

    private TraceContext() {
    }

    public static TraceContext initial(
            @FDesc("系统名称，比如ServiceFramework") String type,
            @FDesc("服务/方法") String api,
            long requestLen,
            String traceId,
            String traceParent,
            String traceSpan
    ) {
        TraceContext context = new TraceContext();
        if (traceId == null) {
            context.traceId = generateTraceId();
            context.requestLen = requestLen;

        } else {
            context.traceId = traceId;
            context.traceParent = traceParent;
            //如果是其他系统的调用，那么该值会由请求系统递增过了的。
            context.traceSpan = traceSpan;
        }

        context.traceAppName = type;
        context.traceAPI = api;
        return context;
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString();
    }

}

