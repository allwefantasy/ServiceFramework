package net.csdn.cluster.routing;

/**
 * User: william
 * Date: 11-9-1
 * Time: 上午11:13
 */
public interface HashFunction {

    int hash(String routing);

    int hash(String type, String id);
}
