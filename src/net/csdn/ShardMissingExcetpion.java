package net.csdn;

/**
 * User: william
 * Date: 11-9-29
 * Time: 下午3:57
 */
public class ShardMissingExcetpion extends CsdnSearchException {


    public ShardMissingExcetpion(String msg) {
        super(msg);
    }

    public ShardMissingExcetpion(String msg, Throwable cause) {
        super(msg, cause);
    }
}
