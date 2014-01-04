package net.csdn.modules.log;

import net.csdn.common.logging.CSLogger;
import org.apache.log4j.Logger;

/**
 * 10/29/13 WilliamZhu(allwefantasy@gmail.com)
 */
public interface SystemLogger {
    Logger applicationLogger();

    Logger behaviorLogger();

    Logger healthLogger();
}
