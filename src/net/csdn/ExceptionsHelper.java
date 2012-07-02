package net.csdn;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午3:26
 */
public class ExceptionsHelper {
    private static final CSLogger logger = Loggers.getLogger(ExceptionsHelper.class);


    public static String detailedMessage(Throwable t) {
        return detailedMessage(t, false, 0);
    }

    public static String detailedMessage(Throwable t, boolean newLines, int initialCounter) {
        if (t == null) {
            return "Unknown";
        }
        int counter = initialCounter + 1;
        if (t.getCause() != null) {
            StringBuilder sb = new StringBuilder();
            while (t != null) {
                if (t.getMessage() != null) {
                    sb.append(t.getClass().getSimpleName()).append("[");
                    sb.append(t.getMessage());
                    sb.append("]");
                    if (!newLines) {
                        sb.append("; ");
                    }
                }
                t = t.getCause();
                if (t != null) {
                    if (newLines) {
                        sb.append("\n");
                        for (int i = 0; i < counter; i++) {
                            sb.append("\t");
                        }
                    } else {
                        sb.append("nested: ");
                    }
                }
                counter++;
            }
            return sb.toString();
        } else {
            return t.getClass().getSimpleName() + "[" + t.getMessage() + "]";
        }
    }

}
