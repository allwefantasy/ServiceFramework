package net.csdn.modules.http.processor;

import net.csdn.common.settings.Settings;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public interface HttpStartProcessor {
    public void process(Settings settings, HttpServletRequest request, HttpServletResponse response, ProcessInfo processInfo);
}
