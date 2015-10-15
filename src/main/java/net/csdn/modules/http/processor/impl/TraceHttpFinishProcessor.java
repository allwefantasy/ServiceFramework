package net.csdn.modules.http.processor.impl;

import net.csdn.common.settings.Settings;
import net.csdn.modules.http.processor.HttpFinishProcessor;
import net.csdn.modules.http.processor.ProcessInfo;
import net.csdn.trace.Trace;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 10/15/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class TraceHttpFinishProcessor implements HttpFinishProcessor {
    @Override
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo) {
        Trace.clean();
    }
}
