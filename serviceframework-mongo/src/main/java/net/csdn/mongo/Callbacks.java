package net.csdn.mongo;

/**
 * User WilliamZhu
 * Date 12-11-4
 * Time 下午845
 */
public class Callbacks {
    public enum Callback {
        before_create,
        after_create,
        before_destroy,
        after_destroy,
        before_save,
        after_save,
        before_update,
        after_update,
        before_validation,
        after_validation
    }

}
