package net.csdn.bootstrap.loader;

import net.csdn.common.settings.Settings;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:29
 */
public interface Loader {
    public void load(Settings settings) throws Exception;
}
