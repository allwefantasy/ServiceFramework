package net.csdn.exception;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-6
 * Time: 上午11:00
 */
public class ArgumentErrorException extends RuntimeException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ArgumentErrorException(String message) {
        super(message);
    }
}
