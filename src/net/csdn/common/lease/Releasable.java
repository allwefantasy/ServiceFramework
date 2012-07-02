package net.csdn.common.lease;

import net.csdn.CsdnSearchException;

/**
 * User: william
 * Date: 11-9-13
 * Time: 下午1:30
 */
public interface Releasable {
    boolean release() throws CsdnSearchException;
}
