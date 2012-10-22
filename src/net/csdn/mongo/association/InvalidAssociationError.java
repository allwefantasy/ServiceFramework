package net.csdn.mongo.association;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午2:25
 */
public class InvalidAssociationError extends RuntimeException {
    public InvalidAssociationError() {
        super("InvalidAssociationError");
    }
}
